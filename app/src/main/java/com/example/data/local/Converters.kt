package com.example.data.local

import androidx.room.TypeConverter
import com.example.domain.model.BlockSource
import com.example.domain.model.ListEntryType
import com.example.domain.model.ListType
import com.example.domain.model.MatchType
import com.example.domain.model.RuleTarget
import com.example.domain.model.SenderType

class Converters {
    @TypeConverter fun fromSenderType(value: SenderType): String = value.name
    @TypeConverter fun toSenderType(value: String): SenderType = SenderType.valueOf(value)

    @TypeConverter fun fromBlockSource(value: BlockSource?): String? = value?.name
    @TypeConverter fun toBlockSource(value: String?): BlockSource? = value?.let { BlockSource.valueOf(it) }

    @TypeConverter fun fromRuleTarget(value: RuleTarget): String = value.name
    @TypeConverter fun toRuleTarget(value: String): RuleTarget = RuleTarget.valueOf(value)

    @TypeConverter fun fromMatchType(value: MatchType): String = value.name
    @TypeConverter fun toMatchType(value: String): MatchType = MatchType.valueOf(value)

    @TypeConverter fun fromListType(value: ListType): String = value.name
    @TypeConverter fun toListType(value: String): ListType = ListType.valueOf(value)

    @TypeConverter fun fromListEntryType(value: ListEntryType): String = value.name
    @TypeConverter fun toListEntryType(value: String): ListEntryType = ListEntryType.valueOf(value)

    @TypeConverter fun fromDeliveryStatus(value: DeliveryStatus): String = value.name
    @TypeConverter fun toDeliveryStatus(value: String): DeliveryStatus = DeliveryStatus.valueOf(value)
}
