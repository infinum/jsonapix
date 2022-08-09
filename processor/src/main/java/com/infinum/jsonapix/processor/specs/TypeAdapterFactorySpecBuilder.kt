package com.infinum.jsonapix.processor.specs

import com.infinum.jsonapix.core.adapters.AdapterFactory
import com.infinum.jsonapix.core.adapters.TypeAdapter
import com.infinum.jsonapix.core.common.JsonApiConstants
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import kotlin.reflect.KClass

public class TypeAdapterFactorySpecBuilder {

    private val classNames = mutableListOf<ClassName>()

    public fun add(className: ClassName) {
        classNames.add(className)
    }

    public fun build(packageName: String): FileSpec {
        return FileSpec.builder(packageName, JsonApiConstants.FileNames.TYPE_ADAPTER_FACTORY)
            .addType(
                TypeSpec.classBuilder(JsonApiConstants.FileNames.TYPE_ADAPTER_FACTORY)
                    .addSuperinterface(AdapterFactory::class)
                    .addFunction(getAdapterFunSpec())
                    .addFunction(getListAdapterFunSpec())
                    .build()
            )
            .apply {
                classNames.forEach {
                    addImport(it.packageName, "TypeAdapter_${it.simpleName}")
                    addImport(it.packageName, "TypeAdapterList_${it.simpleName}")
                }
            }
            .build()
    }

    private fun getAdapterFunSpec(): FunSpec {
        return FunSpec.builder(JsonApiConstants.Members.GET_ADAPTER)
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("type", KClass::class.asClassName().parameterizedBy(WildcardTypeName.producerOf(Any::class)))
            .returns(
                TypeAdapter::class
                    .asClassName()
                    .parameterizedBy(WildcardTypeName.producerOf(Any::class))
                    .copy(nullable = true)
            )
            .beginControlFlow("return when(type.qualifiedName)")
            .apply {
                classNames.forEach {
                    addStatement("%S -> TypeAdapter_${it.simpleName}()", it.canonicalName)
                }
            }
            .addStatement("else -> null")
            .endControlFlow()
            .build()
    }

    private fun getListAdapterFunSpec(): FunSpec {
        return FunSpec.builder(JsonApiConstants.Members.GET_LIST_ADAPTER)
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("type", KClass::class.asClassName().parameterizedBy(WildcardTypeName.producerOf(Any::class)))
            .returns(
                TypeAdapter::class
                    .asClassName()
                    .parameterizedBy(WildcardTypeName.producerOf(Any::class))
                    .copy(nullable = true)
            )
            .beginControlFlow("return when(type.qualifiedName)")
            .apply {
                classNames.forEach {
                    addStatement("%S -> TypeAdapterList_${it.simpleName}()", it.canonicalName)
                }
            }
            .addStatement("else -> null")
            .endControlFlow()
            .build()
    }
}
