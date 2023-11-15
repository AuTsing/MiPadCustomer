package com.autsing.mipadcustomer.hooks

import android.app.AndroidAppHelper
import android.hardware.input.InputManager
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
    companion object {
        val KEY_MAP = mapOf(
            KeyEvent.KEYCODE_1 to KeyEvent.KEYCODE_F1,
            KeyEvent.KEYCODE_2 to KeyEvent.KEYCODE_F2,
            KeyEvent.KEYCODE_3 to KeyEvent.KEYCODE_F3,
            KeyEvent.KEYCODE_4 to KeyEvent.KEYCODE_F4,
            KeyEvent.KEYCODE_5 to KeyEvent.KEYCODE_F5,
            KeyEvent.KEYCODE_6 to KeyEvent.KEYCODE_F6,
            KeyEvent.KEYCODE_7 to KeyEvent.KEYCODE_F7,
            KeyEvent.KEYCODE_8 to KeyEvent.KEYCODE_F8,
            KeyEvent.KEYCODE_9 to KeyEvent.KEYCODE_F9,
            KeyEvent.KEYCODE_0 to KeyEvent.KEYCODE_F10,
            KeyEvent.KEYCODE_MINUS to KeyEvent.KEYCODE_F11,
            KeyEvent.KEYCODE_EQUALS to KeyEvent.KEYCODE_F12,
            KeyEvent.KEYCODE_DEL to KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_DPAD_UP to KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_DPAD_DOWN to KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to KeyEvent.KEYCODE_MOVE_HOME,
            KeyEvent.KEYCODE_DPAD_RIGHT to KeyEvent.KEYCODE_MOVE_END,
        )
    }

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
                            val keyCode = keyEvent.keyCode
                            val down = keyEvent.action == 0

                            if (keyCode == KeyEvent.KEYCODE_SPACE && keyEvent.isCtrlPressed && down) {
                                it.result = -1L
                                return@before
                            }

                            if (keyCode == KeyEvent.KEYCODE_SPACE && keyEvent.isCtrlPressed && !down) {
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
                                return@before
                            }
                        }
                    }

                ClassUtils.loadClass("com.android.server.policy.PhoneWindowManager")
                    .methodFinder()
                    .filterByName("interceptKeyBeforeQueueing")
                    .first()
                    .createHook {
                        before {
                            val keyEvent = it.args[0] as KeyEvent
                            val keyCode = keyEvent.keyCode

                            if (keyEvent.metaState == KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_RIGHT_ON && keyCode in KEY_MAP.keys) {
                                val newKeyEvent = KeyEvent(
                                    keyEvent.downTime,
                                    keyEvent.eventTime,
                                    keyEvent.action,
                                    KEY_MAP[keyCode]!!,
                                    keyEvent.repeatCount,
                                    0,
                                    keyEvent.deviceId,
                                    keyEvent.scanCode,
                                    keyEvent.flags,
                                    keyEvent.source,
                                )

                                val cx = AndroidAppHelper.currentApplication().applicationContext
                                val inputManager = cx.getSystemService(InputManager::class.java)
                                val method = ClassUtils
                                    .loadClass("android.hardware.input.InputManager")
                                    .methodFinder()
                                    .filterByName("injectInputEvent")
                                    .filterByParamCount(2)
                                    .first()
                                method.invoke(inputManager, newKeyEvent, 0)

                                it.result = 0
                                return@before
                            }
                        }
                    }

                Log.ix("Hook ${lpparam.packageName} successfully")
            }.logexIfThrow("Hook ${lpparam.packageName} failed")
        }
    }
}
