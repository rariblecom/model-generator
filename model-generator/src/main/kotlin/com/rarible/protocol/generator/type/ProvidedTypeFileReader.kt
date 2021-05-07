package com.rarible.protocol.generator.type

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File


class ProvidedTypeFileReader(
    private val filePath: File
) : ProvidedTypeReader {

    private val mapper: ObjectMapper = ObjectMapper()

    override fun getMapping(): Map<String, String> {
        val type = mapper.typeFactory.constructMapLikeType(
            HashMap::class.java,
            String::class.java,
            String::class.java
        )

        return mapper.readValue(filePath, type)
    }
}
