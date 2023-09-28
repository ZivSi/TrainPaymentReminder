package com.zs.trainpaymentreminder


const val MINUTE = 60

object GlobalObjectList {
    var delay = 0 // Seconds

    var stationsList: MutableList<StationData> = mutableListOf(
        StationData("Mount Herzl", Location(31.770917, 35.181504)),
        StationData("Yefeh Nof", Location(31.776814, 35.184874)),
        StationData("Denia Square", Location(31.780499, 35.188119)),
        StationData("He-'Haluts", Location(31.782843, 35.191204)),
        StationData("Kiryat Moshe", Location(31.786516, 35.198463)),
        StationData("Ha-Turim", Location(31.787813, 35.206814)),
        StationData("Mahane Yehuda", Location(31.785936, 35.211516)),
        StationData("Ha-Davidka", Location(31.784521, 35.215269)),
        StationData("Jaffa-Center", Location(31.782915, 35.217566)),
        StationData("City Hall", Location(31.779525, 35.224127)),
        StationData("Damascus Gate", Location(31.782479, 35.227689)),
        StationData("Shivtei Israel", Location(31.787485, 35.226887)),
        StationData("Shim'on Ha-Tsadik", Location(31.793313, 35.226329)),
        StationData("Ammunition Hill", Location(31.798999, 35.232026)),
        StationData("Giv'at Ha-Mivtar", Location(31.805615, 35.234186)),
        StationData("Es-Sahl", Location(31.811291, 35.232593)),
        StationData("Shu'afat", Location(31.814292, 35.229914)),
        StationData("Beit 'Hanina", Location(31.819829, 35.229167)),
        StationData("Yekuti'el Adam", Location(31.819400, 35.238972)),
        StationData("Pisgat Ze'ev - Center", Location(31.823786, 35.237479)),
        StationData("Sayeret Dukhifat", Location(31.826056, 35.240191)),
        StationData("'Heil Ha-Avir", Location(31.828999, 35.238257))
        )
}
