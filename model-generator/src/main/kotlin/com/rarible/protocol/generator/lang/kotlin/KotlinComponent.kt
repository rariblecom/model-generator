package com.rarible.protocol.generator.lang.kotlin

import com.rarible.protocol.generator.component.ComponentField
import com.rarible.protocol.generator.component.Discriminator
import com.rarible.protocol.generator.component.GeneratedComponent
import com.rarible.protocol.generator.exception.IllegalOperationException
import com.rarible.protocol.generator.exception.SchemaValidationException
import org.apache.commons.lang3.StringUtils
import java.util.*

class KotlinComponent(
    private val definition: GeneratedComponent
) {

    fun isOneOf(): Boolean {
        return definition.discriminator != null
    }

    fun getAllOneOfComponents(): Collection<GeneratedComponent> {
        val result = HashSet<GeneratedComponent>()
        val subcomponents = getOneOfComponents().map { KotlinComponent(it) }
        for (subcomponent in subcomponents) {
            result.add(subcomponent.definition)
            if (subcomponent.isOneOf()) {
                result.addAll(subcomponent.getAllOneOfComponents())
            }
        }
        return result
    }

    fun getKotlinSingleClass(): KotlinClass {
        return getKotlinSingleClass(null)
    }

    private fun getKotlinSingleClass(oneOfEnum: String?): KotlinClass {
        return KotlinClass(getName(), definition.qualifier, getImports(), getFields(oneOfEnum))
    }

    fun getKotlinMultipleClass(withInheritance: Boolean): KotlinMultipleClass {
        return getKotlinMultipleClass(withInheritance, mapOf())
    }

    private fun getKotlinMultipleClass(
        withInheritance: Boolean,
        parentFields: Map<String, KotlinField>
    ): KotlinMultipleClass {
        val discriminatorFieldName = getDiscriminator().fieldName
        val subcomponents = getOneOfComponents().map { KotlinComponent(it) }
        val leafSubcomponents = getAllOneOfComponents().map { KotlinComponent(it) }.filter { !it.isOneOf() }
        val imports = TreeSet(subcomponents.flatMap { it.getImports() })

        val commonFields = if (withInheritance) getCommonFields(leafSubcomponents) else mutableMapOf()

        val subclasses = ArrayList<KotlinClass>()
        val oneOfMapping = LinkedHashMap<String, String>()
        for (subcomponent in subcomponents) {
            if (subcomponent.isOneOf()) {
                val kotlinClass = subcomponent.getKotlinMultipleClass(withInheritance, commonFields)
                subclasses.add(kotlinClass)
                imports.addAll(kotlinClass.imports)
                oneOfMapping.putAll(kotlinClass.oneOfMapping)
            } else {
                val kotlinClass = subcomponent.getKotlinSingleClass(discriminatorFieldName)
                applyInheritance(kotlinClass, commonFields.keys)
                oneOfMapping[kotlinClass.simpleClassName] = subcomponent.getOneOfEnum(discriminatorFieldName)
                subclasses.add(kotlinClass)
            }
        }

        parentFields.forEach { commonFields.remove(it.key) }

        return KotlinMultipleClass(
            getName(),
            definition.qualifier,
            imports,
            ArrayList(commonFields.values),
            subclasses,
            discriminatorFieldName,
            oneOfMapping
        )
    }

    private fun applyInheritance(kotlinClass: KotlinClass, parentFields: Set<String>) {
        kotlinClass.fields.forEach {
            it.overriden = parentFields.contains(it.name)
        }
        // Not-inherited fields should be last
        Collections.sort(kotlinClass.fields) { f, s -> s.overriden.compareTo(f.overriden) }
    }

    private fun getDiscriminator(): Discriminator {
        if (!isOneOf()) {
            throw IllegalOperationException("Component '${definition.name}' is not a OneOf component")
        }
        return definition.discriminator!!
    }

    private fun getOneOfEnum(discriminatorField: String): String {
        return definition.fields[discriminatorField]!!.enumValues[0]
    }

    private fun getImports(): SortedSet<String> {
        val result = TreeSet<String>()
        for (fieldDefinition in definition.fields.values) {
            result.add(fieldDefinition.type.qualifier)
            for (genericDefinition in fieldDefinition.genericTypes) {
                result.add(genericDefinition.qualifier)
            }
        }
        return result
    }

    // Enums should not be included for oneOf cases
    private fun getFields(discriminatorFieldName: String?): List<KotlinField> {
        val result = ArrayList<KotlinField>()
        for (field in definition.fields.values) {
            if (!field.name.startsWith("@") && field.name != discriminatorFieldName) {
                var kotlinEnum: KotlinEnum? = null
                var filedType: String?
                if (discriminatorFieldName != field.name && field.enumValues.isNotEmpty()) {
                    kotlinEnum = KotlinEnum(
                        field.name.capitalize(),
                        field.enumValues
                    )
                    if (field.type.name == "array") {
                        val collectionType = getSimpleClassName(field.type.qualifier)
                        filedType = "$collectionType<${kotlinEnum.name}>"
                    } else {
                        filedType = kotlinEnum.name
                    }
                } else {
                    filedType = getFieldType(field)
                }

                val kotlinField = KotlinField(
                    field.name,
                    filedType,
                    kotlinEnum,
                    field.isRequired
                )
                result.add(kotlinField)
            }
        }
        return result
    }

    private fun getCommonFields(
        subcomponents: Collection<KotlinComponent>
    ): MutableMap<String, KotlinField> {
        val discriminatorFieldName = getDiscriminator().fieldName
        val commonFields = LinkedHashMap<String, KotlinField>()

        val componentFieldNames = ArrayList<Set<String>>()

        // Have no idea how to find common fields in several collections in better way
        var intersectedFields: MutableSet<String> = HashSet()
        for (definition in subcomponents) {
            val fieldNames = HashSet(definition.getFields(discriminatorFieldName).map { it.name })
            componentFieldNames.add(fieldNames)
            intersectedFields.addAll(fieldNames)
        }
        for (fieldSet in componentFieldNames) {
            intersectedFields = HashSet(intersectedFields.intersect(fieldSet))
        }

        for (definition in subcomponents) {
            val fields = definition.getFields(discriminatorFieldName)
            for (field in fields) {
                if (intersectedFields.contains(field.name)) {
                    val exist = commonFields[field.name]
                    if (exist != null && field != exist) {
                        throw SchemaValidationException(
                            "Common field '${field.name}' type defined differently " +
                                    "in oneOf DTOs: $exist != $field"
                        )
                    }
                    commonFields[field.name] = field
                }
            }
        }
        commonFields.values.forEach { it.abstract = true }
        return commonFields
    }

    private fun getFieldType(field: ComponentField): String {
        var result = getSimpleClassName(field.type.qualifier)
        if (field.genericTypes.isNotEmpty()) {
            val genericList = field.genericTypes.map { getSimpleClassName(it.qualifier) }
            val genericString = StringUtils.join(genericList, ", ")
            result = "$result<$genericString>"
        }
        return result
    }

    private fun getOneOfComponents(): Collection<GeneratedComponent> {
        return getDiscriminator().mapping.values
    }

    private fun getSimpleClassName(fullClassName: String): String {
        return fullClassName.substringAfterLast('.')
    }

    private fun getName(): String {
        return definition.name
    }
}
