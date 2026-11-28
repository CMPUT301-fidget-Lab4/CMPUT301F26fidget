# Lottery and notification refactor

## Original execution flow

The root Compose tree installed `AppEffects` to request Android notification permission, subscribe to unread inbox documents, display alerts, refresh lottery references when profiles changed, and release repository listeners. That same composable started a minute-based loop that queried Firestore for active campsites and asked `LotteryRepository` to draw.

`LotteryRepository` launched a transaction for every active campsite. A transaction opened through a secondary copy before bailing out. The primary transaction checked the registration deadline and `drawn` flag, chose winners, and committed winners, campsite flags, invitations, and inbox documents together. The existing transaction prevented two devices from drawing the same lottery. Putting every entrant’s inbox document in that commit made draw size proportional to the waitlist. A failure to write notifications could also block the draw and its invitations. The aggregate task could report failure even when independent draws had committed.

`NotificationRepository` only exposed inbox reads and acknowledgment. `AppEffects` marked an inbox item read after attempting to post it, even when Android notifications were disabled. Android notification IDs were based on the hash of the Firestore ID.

## Booking schedule reference

`BookingScheduleRepository` retrieves schedules by lottery ID, exposes a removable collection listener, and maps Firestore snapshots to `BookingSchedule`. Each document at `bookingSchedules/{lotteryId}` stores shared and per-campsite stay windows alongside confirmed stays. Dates are represented as ISO local date-time strings. `BookingSchedule` validates shared and per-campsite windows, half-open date ranges, occupancy and the one-stay-per-entrant rule.

Owners initiate schedule creation and editing through lottery forms. Campsite repositories and screens load schedules for calendar display and reservation inputs. Entrants and owners initiate reservation state transitions through `ReservationRepository`. Its transaction reads the current reservation, schedule and campsite capacity before changing the reservation state and confirmed stay. Competing reservations for the final spot are checked against the retried state. Bookings do not have a timer or background execution loop.

The refactor follows the booking design’s split between business decisions, storage, and lifecycle ownership. It also applies its transaction pattern to each draw and to each group of notification recipients. Existing booking issues include overlapping schedule listeners, read methods that collapse some storage and parsing failures, and a reservation transaction that can rewrite a schedule even when the booking list stays the same. Booking operations retain their existing entry points and data schema. [Firestore transaction behavior](https://firebase.google.com/docs/firestore/manage-data/transactions)

## Updated execution flow

1. `MainActivity` starts the minute-based draw check and notification coroutines at Android lifecycle state `STARTED`. They are cancelled when the activity stops and recreated when it starts again. The activity also owns the permission prompt and repository teardown. A new permission grant refreshes the current profile’s unread subscription. [Android lifecycle behavior](https://developer.android.com/reference/androidx/lifecycle/RepeatOnLifecycleKt) · [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
2. `LotteryRepository` uses its existing collection-group listener to identify current documents and group them by lottery ID. The check offers each due lottery one transaction per pass. Drawn lotteries with visible stale campsite flags are also checked. The transaction rereads the canonical copy, verifies the current IDs of participating copies, checks campsite flags and the Alberta registration deadline, and skips replaced, open, inactive or already-drawn lotteries. A previously drawn lottery can clear verified stale flags without writing new results.
3. A successful draw randomly selects at most the configured number of distinct eligible waitlist IDs. It commits winners and `drawn` on all copies, clears active campsite flags and writes invitations with deterministic IDs. The remaining waitlist identifies unsuccessful entrants. An empty waitlist still closes the draw. For a draw with entrants, the same transaction creates `lotteryDrawResults/{lotteryId}` containing the campsite label, remaining recipient IDs and winner IDs. The lottery copies and invitations become visible together with the pending work.
4. `NotificationRepository` has one listener for committed pending results. It generates the appropriate win or loss message and processes up to 100 recipients in each transaction. It checks that a profile exists, creates the deterministic notification document only when missing, and advances or removes the pending document in the same commit. A retry or restart resumes at the remaining recipients. An existing inbox item retains its original data and read state. Draws completed under the previous implementation already generated notifications as part of their original transaction and require no replay. [Firestore listener metadata and cleanup](https://firebase.google.com/docs/firestore/query-data/listen)
5. The recipient’s foreground activity subscribes to its unread inbox. It posts via the existing Android notification channel using the Firestore document ID as a tag. It acknowledges only after posting is allowed and the Android call succeeds. In-memory tracking avoids reposting the same alert when its acknowledgment fails in the same repository instance. The history screen continues to read persisted notifications.
6. Profile deletion removes the deleted profile from pending work and reads the deterministic result, invitation and inbox paths for affected lotteries in its cleanup transaction. This covers concurrent creation of those records for lotteries known to that cleanup.

## Change log

Paths below are relative to the Android app source roots.

| File | Change |
| --- | --- |
| `backEnd/AppEffects.kt` | Removed. Its five responsibilities now have explicit owners. |
| `app/MainActivity.kt` | Owns lifecycle scheduling, permission requests, profile reference refresh and repository cleanup. Removed the Compose-level wiring and the old `checkClosedLotteries` bridge. |
| `data/firebase/LotteryRepository.kt` | Made `drawClosedLotteries` internal and changed it to report committed lottery IDs. Replaced the server campsite query and per-campsite Task wrappers with one transaction per due lottery. Renamed `drawLotteryWinnersTask` to `drawLottery`, replaced `canDrawWinners` with a shared deadline predicate, and folded flag updates into the transaction branches. Replaced the draw-only `primaryOnly` option with a current-ID check and explicit history opt-in for schedule edits. Removed notification document creation from the draw. |
| `data/firebase/NotificationRepository.kt` | Added pending-result delivery, bounded transaction retries and lifecycle-bound unread presentation. Replaced `listenForUnread` and `markRead` with posting plus acknowledgment. Retained `listenForAll` for the history screen. |
| `data/firebase/ReservationRepository.kt` | Removed the unused `addInvitations` batch path, which generated random IDs. Draw transactions own invitation creation. |
| `data/firebase/UserRepository.kt`, `features/profile/ProfileDeletionPlan.kt` | Discover and update pending work during profile deletion and include predictable draw-related paths in the transactional read set. |
| `lottery/LotteryDrawRepositoryTest.kt` | Added seven JVM tests for the draw entry point, including eligibility, shared copies, empty waitlists, stale replacements, repeat draws and failure or concurrent retry. |
| `notification/LotteryNotificationDeliveryTest.kt` | Added six JVM tests for bounded delivery, existing read records, missing profiles, failed commit recovery, observer cancellation and unread acknowledgment retries. |
| `profile/ProfileDeletionPlanTest.kt`, `profile/ProfileDeletionOrchestrationTest.kt`, `profile/UserRepositoryDeletionAuditTest.kt` | Added two profile deletion tests and updated discovery fixtures. |
| `campsite/CampsiteLotterySynchronizationTest.kt` | Supplied a realistic document reference in the existing snapshot fixture. |

## Verification

- Baseline `:app:testDebugUnitTest` passed 157 tests before the refactor.
- The refactored tree passed 172 local JVM tests with `:app:testDebugUnitTest`.
- `:app:compileDebugAndroidTestKotlin` and `:app:assembleDebug` succeeded. Instrumentation tests were compiled. Android device/emulator tests and live Firestore integration were not run.
- The new JVM tests exercise the production transaction bodies with simulated Firestore reads, staged writes and retries. Android alert acknowledgment is tested with an injected posting callback.

## Remaining limits

- Draw checks and delivery run on an active Android client and need Firestore connectivity. With all clients stopped, a due lottery waits for a client to reopen the app. Loaded listings are checked on the minute cycle. Initial snapshots arriving after the first check can add up to one interval. The client clock still determines eligibility.
- Both delivery listeners wait for server-confirmed snapshots. A notification pending on the device waits for connectivity to resume. Android posting and Firestore acknowledgment are separate writes. A process death after posting and before acknowledgment can allow the alert to be shown again.
- The project has no checked-in Firestore security rules or server worker. Deployment must verify permissions for the new pending-result collection, its profile-deletion query, and transactional reads/writes across profiles and inboxes.
- Large lotteries remain subject to Firestore document and transaction limits for the waitlist, copied results, invitations, and pending recipient IDs. Profile cleanup retains its 450-path limit and its discovery boundary for entirely new lotteries outside its query results.
