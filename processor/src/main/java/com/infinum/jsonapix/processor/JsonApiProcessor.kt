package com.infinum.jsonapix.processor

import com.infinum.jsonapix.annotations.JsonApiX
import com.infinum.jsonapix.annotations.JsonApiXLinks
import com.infinum.jsonapix.annotations.JsonApiXMeta
import com.infinum.jsonapix.annotations.LinksPlacementStrategy
import com.infinum.jsonapix.core.common.JsonApiConstants
import com.infinum.jsonapix.core.common.JsonApiConstants.Prefix.withName
import com.infinum.jsonapix.processor.extensions.getAnnotationParameterValue
import com.infinum.jsonapix.processor.specs.*
import com.infinum.jsonapix.processor.specs.jsonxextensions.JsonXExtensionsSpecBuilder
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.metadata.classinspectors.ElementsClassInspector
import com.squareup.kotlinpoet.metadata.specs.toTypeSpec
import com.squareup.kotlinpoet.metadata.toKmClass
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic


@SuppressWarnings("SpreadOperator")
public class JsonApiProcessor : AbstractProcessor() {

    private val collector = JsonXExtensionsSpecBuilder()
    private val adapterFactoryCollector = TypeAdapterFactorySpecBuilder()
    private val customLinks = mutableListOf<LinksInfo>()
    private val customMetas = mutableMapOf<String, ClassName>()

    override fun getSupportedAnnotationTypes(): MutableSet<String> =
        mutableSetOf(JsonApiX::class.java.name, JsonApiXLinks::class.java.name, JsonApiXMeta::class.java.name)

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    override fun process(
        annotations: MutableSet<out TypeElement>?,
        roundEnv: RoundEnvironment?
    ): Boolean {
        val linksElements = roundEnv?.getElementsAnnotatedWith(JsonApiXLinks::class.java).orEmpty().map {
            val type = it.getAnnotationParameterValue<JsonApiXLinks, String> { type }
            val placementStrategy =
                it.getAnnotationParameterValue<JsonApiXLinks, LinksPlacementStrategy> { placementStrategy }
            val className = ClassName(processingEnv.elementUtils.getPackageOf(it).toString(), it.simpleName.toString())
            customLinks.firstOrNull { linksInfo -> linksInfo.type == type }?.let { linksInfo ->
                when (placementStrategy) {
                    LinksPlacementStrategy.ROOT -> linksInfo.rootLinks = className.canonicalName
                    LinksPlacementStrategy.DATA -> linksInfo.resourceObjectLinks = className.canonicalName
                    LinksPlacementStrategy.RELATIONSHIPS -> linksInfo.relationshipsLinks = className.canonicalName
                }
            } ?: kotlin.run {
                val linksInfo = LinksInfo(type)
                when (placementStrategy) {
                    LinksPlacementStrategy.ROOT -> linksInfo.rootLinks = className.canonicalName
                    LinksPlacementStrategy.DATA -> linksInfo.resourceObjectLinks = className.canonicalName
                    LinksPlacementStrategy.RELATIONSHIPS -> linksInfo.relationshipsLinks = className.canonicalName
                }
                customLinks.add(linksInfo)
            }
            className
        }

        collector.addCustomLinks(linksElements)

        roundEnv?.getElementsAnnotatedWith(JsonApiXMeta::class.java).orEmpty().forEach {
            val type = it.getAnnotationParameterValue<JsonApiXMeta, String> { type }
            val className = ClassName(processingEnv.elementUtils.getPackageOf(it).toString(), it.simpleName.toString())
            customMetas[type] = className
        }

        collector.addCustomMetas(customMetas)

        val elements = roundEnv?.getElementsAnnotatedWith(JsonApiX::class.java)
        // process method might get called multiple times and not finding elements is a possibility
        if (!elements.isNullOrEmpty()) {
            var rootPackage: String? = null
            elements.forEach {
                if (rootPackage == null) {
                    var enclosing: Element = it
                    while (enclosing.kind != ElementKind.PACKAGE) {
                        enclosing = enclosing.enclosingElement
                    }
                    rootPackage = processingEnv.elementUtils.getPackageOf(enclosing).toString()
                }

                if (it.kind != ElementKind.CLASS) {
                    processingEnv.messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Only classes can be annotated"
                    )
                    return true
                }

                rootPackage?.apply {
                    val type = it.getAnnotationParameterValue<JsonApiX, String> { type }
                    processAnnotation(this, it, type)
                }
            }

            rootPackage?.apply {
                val kaptKotlinGeneratedDir = processingEnv.options[JsonApiConstants.KAPT_KOTLIN_GENERATED_OPTION_NAME]
                collector.build(this).writeTo(File(kaptKotlinGeneratedDir!!))
                adapterFactoryCollector.build(this).writeTo(File(kaptKotlinGeneratedDir))
            }
        }
        return true
    }

    @SuppressWarnings("LongMethod")
    private fun processAnnotation(rootPackage: String, element: Element, type: String) {
        val className = element.simpleName.toString()
        val generatedPackage = processingEnv.elementUtils.getPackageOf(element).toString()
        val kaptKotlinGeneratedDir =
            processingEnv.options[JsonApiConstants.KAPT_KOTLIN_GENERATED_OPTION_NAME]

        val metadata = element.getAnnotation(Metadata::class.java)
        val typeSpec = metadata.toKmClass().toTypeSpec(
            ElementsClassInspector.create(processingEnv.elementUtils, processingEnv.typeUtils)
        )

        val inputDataClass = ClassName(generatedPackage, className)
        val generatedJsonWrapperName = JsonApiConstants.Prefix.JSON_API_X.withName(className)
        val generatedJsonWrapperListName = JsonApiConstants.Prefix.JSON_API_X_LIST.withName(className)
        val generatedResourceObjectName =
            JsonApiConstants.Prefix.RESOURCE_OBJECT.withName(className)

        val jsonWrapperClassName = ClassName(generatedPackage, generatedJsonWrapperName)
        val jsonWrapperListClassName = ClassName(generatedPackage, generatedJsonWrapperListName)
        val resourceObjectClassName = ClassName(generatedPackage, generatedResourceObjectName)

        var attributesClassName: ClassName? = null
        var relationshipsClassName: ClassName? = null
        val membersSeparator = PropertyTypesSeparator(typeSpec)
        val primitives = membersSeparator.getPrimitiveProperties()

        if (primitives.isNotEmpty()) {
            val attributesTypeSpec =
                AttributesSpecBuilder.build(
                    ClassName(generatedPackage, className),
                    primitives,
                    type
                )
            val attributesFileSpec = FileSpec.builder(generatedPackage, attributesTypeSpec.name!!)
                .addType(attributesTypeSpec).build()

            attributesFileSpec.writeTo(File(kaptKotlinGeneratedDir!!))

            val generatedAttributesObjectName =
                JsonApiConstants.Prefix.ATTRIBUTES.withName(className)
            attributesClassName =
                ClassName(generatedPackage, generatedAttributesObjectName)
        }

        val oneRelationships = membersSeparator.getOneRelationships()
        val manyRelationships = membersSeparator.getManyRelationships()

        if (oneRelationships.isNotEmpty() || manyRelationships.isNotEmpty()) {
            val relationshipsTypeSpec = RelationshipsSpecBuilder.build(
                inputDataClass,
                type,
                oneRelationships,
                manyRelationships
            )

            val relationshipsFileSpec =
                FileSpec.builder(generatedPackage, relationshipsTypeSpec.name!!)
                    .addType(relationshipsTypeSpec)
                    .build()
            relationshipsFileSpec.writeTo(File(kaptKotlinGeneratedDir!!))

            val generatedRelationshipsObjectName =
                JsonApiConstants.Prefix.RELATIONSHIPS.withName(className)
            relationshipsClassName = ClassName(generatedPackage, generatedRelationshipsObjectName)
        }

        collector.add(
            type,
            inputDataClass,
            jsonWrapperClassName,
            jsonWrapperListClassName,
            resourceObjectClassName,
            attributesClassName,
            relationshipsClassName,
            IncludedSpecBuilder.build(
                oneRelationships,
                manyRelationships
            ),
            IncludedSpecBuilder.buildForList(
                oneRelationships,
                manyRelationships
            )
        )

        adapterFactoryCollector.add(inputDataClass)

        val resourceFileSpec =
            ResourceObjectSpecBuilder.build(
                inputDataClass,
                type,
                primitives,
                mapOf(*oneRelationships.map { it.name to it.type }.toTypedArray()),
                mapOf(*manyRelationships.map { it.name to it.type }.toTypedArray())
            )
        val wrapperFileSpec =
            JsonApiXSpecBuilder.build(inputDataClass, type, customMetas[type])
        val wrapperListFileSpec =
            JsonApiXListSpecBuilder.build(inputDataClass, type, customMetas[type])
        val linksInfo = customLinks.firstOrNull { it.type == type }

        val typeAdapterFileSpec = TypeAdapterSpecBuilder.build(
            rootPackage,
            inputDataClass,
            linksInfo?.rootLinks,
            linksInfo?.resourceObjectLinks,
            linksInfo?.relationshipsLinks,
            customMetas[type]?.canonicalName
        )

        val typeAdapterListFileSpec = TypeAdapterListSpecBuilder.build(
            rootPackage,
            inputDataClass,
            linksInfo?.rootLinks,
            linksInfo?.resourceObjectLinks,
            linksInfo?.relationshipsLinks,
            customMetas[type]?.canonicalName
        )

        resourceFileSpec.writeTo(File(kaptKotlinGeneratedDir!!))
        wrapperFileSpec.writeTo(File(kaptKotlinGeneratedDir))
        wrapperListFileSpec.writeTo(File(kaptKotlinGeneratedDir))
        typeAdapterFileSpec.writeTo(File(kaptKotlinGeneratedDir))
        typeAdapterListFileSpec.writeTo(File(kaptKotlinGeneratedDir))
    }
}
