package com.autsing.mipadcustomer.hooks

import android.app.AndroidAppHelper
import android.provider.Settings
import android.view.KeyEvent
import com.github.kyuubiran.ezxhelper.ClassUtils
import com.github.kyuubiran.ezxhelper.EzXHelper
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHook
import com.github.kyuubiran.ezxhelper.Log
import com.github.kyuubiran.ezxhelper.LogExtensions.logexIfThrow
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

const val TAG = "mipadcustomer"

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "android") {
            EzXHelper.initHandleLoadPackage(lpparam)
            EzXHelper.setLogTag(TAG)
            EzXHelper.setToastTag(TAG)

            runCatching {
                ClassUtils.loadClass("com.android.server.policy.PhoneWindowManager")
                    .methodFinder()
                    .filterByName("interceptKeyBeforeDispatching")
                    .first()
                    .createHook {
                        before {
                            val keyEvent = it.args[1] as KeyEvent
                            if (keyEvent.action != 0 && keyEvent.isCtrlPressed && keyEvent.keyCode == KeyEvent.KEYCODE_SPACE) {
                                val cr = AndroidAppHelper.currentApplication().contentResolver
                                val enabledInputMethods = Settings.Secure.getString(
                                    cr,
                                    Settings.Secure.ENABLED_INPUT_METHODS,
                                ).split(":")
                                val defaultInputMethod = Settings.Secure.getString(
                                    cr,
                                    Settings.Secure.DEFAULT_INPUT_METHOD,
                                )
                                val nextInputMethodIndex =
                                    enabledInputMethods.indexOf(defaultInputMethod) + 1
                                val nextInputMethod =
                                    enabledInputMethods.getOrNull(nextInputMethodIndex)
                                        ?: enabledInputMethods[0]
                                Settings.Secure.putString(
                                    cr,
                                    Settings.Secure.DEFAULT_INPUT_METHOD,
                                    nextInputMethod,
                                )
                                it.result = -1L
                            }
                        }
                    }
                Log.ix("Hook ${lpparam.packageName} successfully")
            }.logexIfThrow("Hook ${lpparam.packageName} failed")
        }
    }
}
