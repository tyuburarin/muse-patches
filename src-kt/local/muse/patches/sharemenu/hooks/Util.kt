package local.muse.patches.sharemenu.hooks

import local.muse.patches.sharemenu.fingerprints.addAction
import app.morphe.patcher.patch.BytecodePatchContext

context(BytecodePatchContext)
fun shareMenuButtonInjection(
    actionName: String,
    prefFunctionName: String,
    stringId: String,
    iconId: String,
) {
    // Add action
    val buttonActionReference = addAction(actionName)

    // Register button
    registerButton(buttonActionReference, prefFunctionName)

    // Set Button Text
    setButtonText(actionName, stringId)
    setButtonIcon(actionName, iconId)
}
