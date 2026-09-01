package com.vedryxtech.voiceagent.mapper;

import org.bson.types.ObjectId;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class MapperSupport {

    @Named("objectIdToString")
    public String objectIdToString(ObjectId value) {
        return value == null ? null : value.toHexString();
    }

    @Named("integerToInt")
    public int integerToInt(Integer value) {
        return value == null ? 0 : value;
    }
}
