package com.quietledger.database

import androidx.room.TypeConverter
import com.quietledger.models.*

class Converters {
    @TypeConverter fun typeToString(v: TransactionType) = v.name
    @TypeConverter fun stringToType(v: String) = TransactionType.valueOf(v)
    @TypeConverter fun catToString(v: Category) = v.name
    @TypeConverter fun stringToCat(v: String) = Category.valueOf(v)
    @TypeConverter fun statusToString(v: TransactionStatus) = v.name
    @TypeConverter fun stringToStatus(v: String) = TransactionStatus.valueOf(v)
}
