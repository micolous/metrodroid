package au.id.micolous.metrodroid.transit.suica

/** Appears on all IC cards */
const val SYSTEMCODE_SUICA = 0x0003
/** Appears only on Suica, functionality unknown */
const val SYSTEMCODE_SUICA_UNKNOWN = 0x86a7
/** Appears only on Hayakaken, functionality unknown */
const val SYSTEMCODE_HAYAKAKEN = 0x927a

const val SERVICE_SUICA_ID = 0x008b
const val SERVICE_SUICA_HISTORY = 0x090f
const val SERVICE_SUICA_INOUT = 0x108f
const val SERVICE_SUICA_ADMISSION = 0x10cb
