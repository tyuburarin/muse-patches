package local.muse.patches.links

import local.muse.patches.MuseConstants.COMPATIBILITY_X
import local.muse.utils.extendWithBundled
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11x

private const val URL_ENTITY_MAPPER_CLASS =
    "Lcom/twitter/model/json/core/JsonUrlEntity\$\$JsonObjectMapper;"
private const val TIMELINE_URL_MAPPER_CLASS =
    "Lcom/twitter/model/json/timeline/urt/JsonTimelineUrl\$\$JsonObjectMapper;"
private const val CLEANER_CLASS =
    "Llocal/muse/extension/twitter/patches/links/AffiliateLinkCleaner;"

/**
 * The typed parse method of the URL-entity JSON mapper,
 * (Lcom/fasterxml/jackson/core/h;)Lcom/twitter/model/json/core/JsonUrlEntity;.
 *
 * Verified against X 12.19.1: the entity is built in v0 and the final
 * instruction is `return-object v0` (there is one earlier return of a
 * null constant for the empty-input case, which must stay untouched).
 * The bridge parse (return type Ljava/lang/Object;) is a different
 * method and is excluded by the return type here.
 */
internal object JsonUrlEntityParseFingerprint : Fingerprint(
    definingClass = URL_ENTITY_MAPPER_CLASS,
    name = "parse",
    returnType = "Lcom/twitter/model/json/core/JsonUrlEntity;",
    parameters = listOf("Lcom/fasterxml/jackson/core/h;"),
)

/**
 * The typed parse method of the URT timeline URL mapper. URT link
 * entries carry their own URL model (a = expanded_url, b = url),
 * separate from post text entities. Verified against X 12.19.1: the
 * entity is built in v0 and the final instruction is
 * `return-object v0`; the one earlier return is the null case.
 */
internal object JsonTimelineUrlParseFingerprint : Fingerprint(
    definingClass = TIMELINE_URL_MAPPER_CLASS,
    name = "parse",
    returnType = "Lcom/twitter/model/json/timeline/urt/JsonTimelineUrl;",
    parameters = listOf("Lcom/fasterxml/jackson/core/h;"),
)

/**
 * "Strip affiliate links" — アフィリンクを外して踏む.
 *
 * Every URL entity in every post passes through this one mapper when
 * the API response is parsed, so cleaning here covers timeline posts,
 * post details, profiles and DMs at once, before any tap can happen.
 *
 * The insertion calls AffiliateLinkCleaner.clean(entity) just before
 * the final return. The cleaner (extension side, plain Java + reflection
 * over the entity's b/c/e fields):
 *  - resolves known short/affiliate redirectors (amzn.to, a.r10.to,
 *    hb.afl.rakuten.co.jp, bit.ly, ...) by following redirects itself,
 *    without storing cookies and without fetching the merchant page;
 *  - strips affiliate/tracking parts with the same rules as the
 *    アフィリンク外し Space (Amazon ASIN normalisation to /dp/<ASIN>,
 *    tag/scid/utm/click-id removal, product params kept);
 *  - writes the cleaned expanded URL back into the entity's url
 *    fields, so the tap opens the clean destination directly instead
 *    of the t.co link with the affiliate parts attached (the same
 *    field Piko's "No shortened URL" patch rewrites).
 *
 * The call takes Ljava/lang/Object; so no compile-time stub of the
 * app's JsonUrlEntity is needed on the extension side; the entity
 * register's runtime type is a subtype, which the verifier accepts
 * for a static call. The method returns void and mutates in place,
 * so the return register keeps its JsonUrlEntity type.
 *
 * Fails open by construction: the cleaner catches everything, and if
 * the fingerprint shape ever differs from 12.19.1 the patch aborts
 * instead of inserting into the wrong place.
 */
/**
 * Inserts `AffiliateLinkCleaner.<cleanerMethod>(entity)` just before
 * the final RETURN_OBJECT of the fingerprint's parse method, after
 * checking the match is exactly the class/method it claims to be.
 */
private fun insertCleanCall(
    fingerprint: Fingerprint,
    expectedClass: String,
    cleanerMethod: String,
) {
    if (fingerprint.classDef.type != expectedClass) {
        throw PatchException(
            "Mapper fingerprint matched an unexpected class: ${fingerprint.classDef.type}",
        )
    }
    val method = fingerprint.method
    if (method.name != "parse") {
        throw PatchException(
            "Mapper fingerprint matched an unexpected method: ${method.name}",
        )
    }

    val code = method.instructions.toList()
    val returnIndex = code.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
    if (returnIndex < 0) {
        throw PatchException("No RETURN_OBJECT found in parse of $expectedClass")
    }
    val returnRegister =
        (method.getInstruction<Instruction11x>(returnIndex)).registerA

    method.addInstructions(
        returnIndex,
        """
        invoke-static { v$returnRegister }, $CLEANER_CLASS->$cleanerMethod(Ljava/lang/Object;)V
        """.trimIndent(),
    )
}

@Suppress("unused")
val stripAffiliateLinksPatch =
    bytecodePatch(
        name = "Strip affiliate links",
        description = "投稿内のリンクを開く前にアフィリエイト・トラッキング部分を外し、t.co 短縮も解除して展開先を直接開きます。Amazon は /dp/<ASIN> に正規化、短縮リンク (amzn.to / link.amazon / a.r10.to 等) はリダイレクトを解決してからクリーニングします。",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)

        extendWithBundled("extensions/muse-patches.dex")

        execute {
            insertCleanCall(
                JsonUrlEntityParseFingerprint,
                URL_ENTITY_MAPPER_CLASS,
                "clean",
            )
            insertCleanCall(
                JsonTimelineUrlParseFingerprint,
                TIMELINE_URL_MAPPER_CLASS,
                "cleanTimelineUrl",
            )
        }
    }
