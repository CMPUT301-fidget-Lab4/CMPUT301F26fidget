package com.example.myapplication.features



class Services(
    private val lotteryRepo: LotteryRepository,
    private val campsiteRepo: CampsiteRepository
) {

    // This class is should act class that everyone should use to contact the DataBase.
    // Like this class will be for us to use when we create a Lottery repo or a campsiteRepo
    // At the start of the program. Coordinate operations across multiple campsites or repositories

    /**
     * Checks whether all campsites exist and are available
     * to participate in a new lottery.
     *
     * @return true if all campsites are available.
     */
    fun canCreateLottery(ids: List<String>): Boolean {
        return !campsiteRepo.anyInLottery(ids)
    }

    /**
     * Marks all participating campsites as being in a lottery.
     */
    fun markCampsitesInLottery(ids: List<String>) {
        for (id in ids) {
            val campsite = campsiteRepo.findCampsite(id)

            if (campsite != null) {
                campsite.inLottery = true
                campsiteRepo.updateCampsiteField(
                    campsite.name,
                    "inLottery",
                    true
                )
            }
        }
    }
}