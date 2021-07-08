package com.rarible.protocol.generator.lang.kotlin

import com.rarible.protocol.generator.component.ComponentField
import com.rarible.protocol.generator.component.GeneratedComponent
import com.rarible.protocol.generator.lang.LangComponent
import com.rarible.protocol.generator.lang.LangEnum

class KotlinComponent(
    parent: LangComponent?,
    definition: GeneratedComponent
) : LangComponent(
    parent,
    definition
) {
    override fun createFieldEnum(field: ComponentField): LangEnum {
        return LangEnum(
            field.name.capitalize(),
            field.enumValues
        )
    }

    override fun getSimpleClassName(qualifier: String): String {
        return qualifier.substringAfterLast('.')
    }

    override fun fromComponent(parent: LangComponent, definition: GeneratedComponent): LangComponent {
        return KotlinComponent(parent, definition)
    }
}