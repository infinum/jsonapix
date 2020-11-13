package com.infinum.jsonapix.processor.specs

import com.infinum.jsonapix.core.JsonApiWrapper
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.asClassName
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

internal class JsonApiExtensionsSpecBuilder {

    companion object {
        private const val EXTENSIONS_PACKAGE = "com.infinum.jsonapix"
        private const val EXTENSIONS_FILE_NAME = "JsonApiExtensions"
        private const val WRAPPER_GETTER_FUNCTION_NAME = "toJsonApiWrapper"
        private const val SERIALIZER_EXTENSION_NAME = "toJsonApiString"
        private const val DESERIALIZER_EXTENSION_NAME = "decodeJsonApiString"
        private const val FORMAT_PROPERTY_NAME = "format"
        private const val SERIALIZERS_MODULE_PROPERTY_NAME = "jsonApiSerializerModule"
        private const val KOTLINX_SERIALIZATION_PACKAGE = "kotlinx.serialization"
        private const val KOTLINX_SERIALIZATION_MODULES_PACKAGE = "kotlinx.serialization.modules"
        private const val POLYMORPHIC_FUNCTION_NAME = "polymorphic"
        private const val SUBCLASS_FUNCTION_NAME = "subclass"
        private const val CLASS_DISCRIMINATOR = "#class"

        private const val JSON_API_WRAPPER_IMPORT = "core.JsonApiWrapper"

        private val KOTLINX_IMPORTS = arrayOf(
            "json.Json",
            "encodeToString",
            "decodeFromString",
            "PolymorphicSerializer"
        )

        private val KOTLINX_MODULES_IMPORTS = arrayOf(
            "polymorphic",
            "subclass",
            "SerializersModule"
        )
    }

    private val specsMap = hashMapOf<ClassName, ClassName>()

    fun add(data: ClassName, wrapper: ClassName) {
        specsMap[data] = wrapper
    }

    private fun deserializeFunSpec(): FunSpec {
        val typeVariableName = TypeVariableName.invoke("T")
        val decodeMember = MemberName(KOTLINX_SERIALIZATION_PACKAGE, "decodeFromString")
        return FunSpec.builder(DESERIALIZER_EXTENSION_NAME)
            .receiver(String::class)
            .addModifiers(KModifier.INLINE)
            .addTypeVariable(typeVariableName.copy(reified = true))
            .returns(typeVariableName)
            .addStatement(
                "return format.%M<%T<%T>>(this).data",
                decodeMember,
                JsonApiWrapper::class,
                typeVariableName
            )
            .build()
    }

    private fun formatPropertySpec(): PropertySpec {
        val formatCodeBuilder = CodeBlock.builder()
            .addStatement("%T {", Json::class)
            .indent()
            .addStatement("encodeDefaults = true")
            .addStatement("classDiscriminator = %S", CLASS_DISCRIMINATOR)
            .addStatement("serializersModule = %L", SERIALIZERS_MODULE_PROPERTY_NAME)
            .unindent()
            .addStatement("}")
        return PropertySpec.builder(FORMAT_PROPERTY_NAME, Json::class)
            .initializer(formatCodeBuilder.build())
            .build()
    }

    private fun serializerPropertySpec(): PropertySpec {
        val codeBlockBuilder = CodeBlock.builder()
        val polymorpicMember = MemberName(
            KOTLINX_SERIALIZATION_MODULES_PACKAGE,
            POLYMORPHIC_FUNCTION_NAME
        )
        val subclassMember = MemberName(
            KOTLINX_SERIALIZATION_MODULES_PACKAGE,
            SUBCLASS_FUNCTION_NAME
        )
        codeBlockBuilder.addStatement("%T {", SerializersModule::class)
        codeBlockBuilder.indent()
            .addStatement("%M(%T::class) {", polymorpicMember, JsonApiWrapper::class)
        codeBlockBuilder.indent()
        specsMap.values.forEach {
            codeBlockBuilder.addStatement("%M(%T::class)", subclassMember, it)
        }
        codeBlockBuilder.unindent().addStatement("}")
        codeBlockBuilder.unindent().addStatement("}")

        return PropertySpec.builder(SERIALIZERS_MODULE_PROPERTY_NAME, SerializersModule::class)
            .initializer(codeBlockBuilder.build()).build()
    }

    @SuppressWarnings("SpreadOperator")
    fun build(): FileSpec {
        val fileSpec = FileSpec.builder(EXTENSIONS_PACKAGE, EXTENSIONS_FILE_NAME)
        fileSpec.addAnnotation(
            AnnotationSpec.builder(JvmName::class).addMember("%S", EXTENSIONS_FILE_NAME)
                .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE).build()
        )

        fileSpec.addImport(
            KOTLINX_SERIALIZATION_PACKAGE,
            *KOTLINX_IMPORTS
        )

        fileSpec.addImport(
            KOTLINX_SERIALIZATION_MODULES_PACKAGE,
            *KOTLINX_MODULES_IMPORTS
        )

        fileSpec.addImport(EXTENSIONS_PACKAGE, JSON_API_WRAPPER_IMPORT)

        specsMap.entries.forEach {
            fileSpec.addFunction(
                FunSpec.builder(WRAPPER_GETTER_FUNCTION_NAME)
                    .receiver(it.key)
                    .returns(it.value)
                    .addStatement("return %T(this)", it.value)
                    .build()
            )

            val polymorphicSerializerClass = PolymorphicSerializer::class.asClassName()
            val jsonApiWrapperClass = JsonApiWrapper::class.asClassName()
            fileSpec.addFunction(
                FunSpec.builder(SERIALIZER_EXTENSION_NAME)
                    .receiver(it.key)
                    .returns(String::class)
                    .addStatement(
                        "return format.encodeToString(%T(%T::class), this.%L())",
                        polymorphicSerializerClass,
                        jsonApiWrapperClass,
                        WRAPPER_GETTER_FUNCTION_NAME
                    )
                    .build()
            )
        }

        fileSpec.addProperty(serializerPropertySpec())
        fileSpec.addProperty(formatPropertySpec())
        fileSpec.addFunction(deserializeFunSpec())

        return fileSpec.build()
    }
}
