package com.infinum.jsonapix.processor.specs.model

import com.infinum.jsonapix.core.common.JsonApiConstants
import com.infinum.jsonapix.core.resources.DefaultLinks
import com.infinum.jsonapix.core.resources.Error
import com.infinum.jsonapix.core.resources.Meta
import com.infinum.jsonapix.processor.LinksInfo
import com.infinum.jsonapix.processor.MetaInfo
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asClassName

internal object JsonApiModelSpecBuilder : BaseJsonApiModelSpecBuilder() {
    override fun getClassSuffixName(): String = JsonApiConstants.Suffix.JSON_API_MODEL
    override fun getRootClassName(rootType: ClassName): ClassName = rootType
    override fun getParams(className: ClassName, isRootNullable: Boolean, metaInfo: MetaInfo?, linksInfo: LinksInfo?): List<ParameterSpec> {
        return listOf(
            JsonApiConstants.Keys.DATA.asParam(
                getRootClassName(className),
                isRootNullable,
                JsonApiConstants.Defaults.NULL.takeIf { isRootNullable }
            ),
            JsonApiConstants.Members.ROOT_LINKS.asParam(
                linksInfo?.rootLinks ?: DefaultLinks::class.asClassName(),
                true,
                JsonApiConstants.Defaults.NULL
            )
            ,
            JsonApiConstants.Members.RESOURCE_OBJECT_LINKS.asParam(
                linksInfo?.resourceObjectLinks ?: DefaultLinks::class.asClassName(),
                true,
                JsonApiConstants.Defaults.NULL
            )
            ,
            JsonApiConstants.Members.RELATIONSHIPS_LINKS.asParam(
                Map::class.asClassName().parameterizedBy(
                    String::class.asClassName(),
                    linksInfo?.relationshipsLinks?.copy(nullable = true) ?: DefaultLinks::class.asClassName().copy(nullable = true),
                ),
                true,
                JsonApiConstants.Defaults.EMPTY_MAP,
            ),
            JsonApiConstants.Keys.ERRORS.asParam(
                List::class.asClassName().parameterizedBy(Error::class.asClassName()),
                true,
                JsonApiConstants.Defaults.NULL,
            ),
            JsonApiConstants.Members.ROOT_META.asParam(
                metaInfo?.rootClassName ?: Meta::class.asClassName(),
                true,
                JsonApiConstants.Defaults.NULL,
            ),
            JsonApiConstants.Members.RESOURCE_OBJECT_META.asParam(
                metaInfo?.resourceObjectClassName ?: Meta::class.asClassName(),
                true,
                JsonApiConstants.Defaults.NULL,
            ),
            JsonApiConstants.Members.RELATIONSHIPS_META.asParam(
                Map::class.asClassName().parameterizedBy(
                    String::class.asClassName(),
                    metaInfo?.relationshipsClassNAme?.copy(nullable = true) ?: Meta::class.asClassName().copy(nullable = true),
                ),
                true,
                JsonApiConstants.Defaults.EMPTY_MAP,
            )
        )
    }
}
