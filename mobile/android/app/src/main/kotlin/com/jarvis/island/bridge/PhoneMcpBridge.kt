package com.jarvis.island.bridge

import android.content.Context
import android.util.Log
import com.jarvis.island.automation.CommandRouter
import com.jarvis.island.automation.ScreenAutomation
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phone-to-Desktop Model Context Protocol (MCP) Server Bridge.
 *
 * Exposes the phone's accessibility and system tools over the LiveBridge TLS port
 * as a standard JSON-RPC 2.0 MCP server, allowing desktop coding agents (Claude Code,
 * Antigravity, Cursor) to directly automate and inspect the mobile device.
 */
object PhoneMcpBridge {

    private const val TAG = "JarvisPhoneMcp"

    fun handleRequest(context: Context, jsonBody: String): String {
        return try {
            val req = JSONObject(jsonBody)
            val id = req.opt("id") ?: 1
            val method = req.optString("method")
            val params = req.optJSONObject("params") ?: JSONObject()

            when (method) {
                "initialize" -> {
                    val res = JSONObject().apply {
                        put("protocolVersion", "2024-11-05")
                        put("serverInfo", JSONObject().apply {
                            put("name", "jarvis-android-mcp")
                            put("version", com.jarvis.island.BuildConfig.VERSION_NAME)
                        })
                        put("capabilities", JSONObject().apply {
                            put("tools", JSONObject())
                        })
                    }
                    formatResponse(id, res)
                }

                "tools/list" -> {
                    val toolsArr = JSONArray().apply {
                        put(makeTool("tap", "Tap an on-screen element by its element ID or relative coordinates",
                            JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("eid", JSONObject().put("type", "integer").put("description", "Element ID from screen outline"))
                                    put("x", JSONObject().put("type", "number").put("description", "Relative X coordinate (0.0 to 1.0)"))
                                    put("y", JSONObject().put("type", "number").put("description", "Relative Y coordinate (0.0 to 1.0)"))
                                })
                            }))
                        put(makeTool("type", "Type text into currently focused editable field",
                            JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("text", JSONObject().put("type", "string").put("description", "Text to input"))
                                    put("clearFirst", JSONObject().put("type", "boolean").put("description", "Clear existing text before typing"))
                                })
                                put("required", JSONArray().put("text"))
                            }))
                        put(makeTool("scroll", "Scroll vertically or horizontally",
                            JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("direction", JSONObject().put("type", "string").put("enum", JSONArray().put("up").put("down").put("left").put("right")))
                                    put("times", JSONObject().put("type", "integer").put("default", 1))
                                })
                            }))
                        put(makeTool("swipe", "Perform a continuous swipe gesture",
                            JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("direction", JSONObject().put("type", "string").put("enum", JSONArray().put("up").put("down").put("left").put("right")))
                                })
                            }))
                        put(makeTool("open_app", "Launch an application on the device",
                            JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("name", JSONObject().put("type", "string").put("description", "Application name (e.g. WhatsApp, Chrome, Settings)"))
                                })
                                put("required", JSONArray().put("name"))
                            }))
                        put(makeTool("get_screen_outline", "Inspect active screen hierarchy and interactive elements with element IDs",
                            JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("maxElements", JSONObject().put("type", "integer").put("default", 60))
                                })
                            }))
                        put(makeTool("run_command", "Execute a natural language JARVIS command",
                            JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("command", JSONObject().put("type", "string").put("description", "Instruction (e.g. 'send WhatsApp to Mom saying Hello')"))
                                })
                                put("required", JSONArray().put("command"))
                            }))
                        put(makeTool("global_action", "Trigger global navigation (back, home, recents, notifications)",
                            JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("action", JSONObject().put("type", "string").put("enum", JSONArray().put("back").put("home").put("recents").put("notifications")))
                                })
                                put("required", JSONArray().put("action"))
                            }))
                    }
                    val res = JSONObject().put("tools", toolsArr)
                    formatResponse(id, res)
                }

                "tools/call" -> {
                    val name = params.optString("name")
                    val args = params.optJSONObject("arguments") ?: JSONObject()
                    val outputText = executeTool(context, name, args)

                    val contentArr = JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", outputText)
                        })
                    }
                    val res = JSONObject().put("content", contentArr)
                    formatResponse(id, res)
                }

                else -> {
                    formatError(id, -32601, "Method not found: $method")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MCP processing failure: ${e.message}", e)
            formatError(1, -32603, "Internal MCP error: ${e.message}")
        }
    }

    private fun executeTool(context: Context, name: String, args: JSONObject): String {
        return when (name) {
            "tap" -> {
                val eid = args.optInt("eid", -1)
                if (eid >= 0) {
                    val ok = ScreenAutomation.tap(eid)
                    if (ok) "Tapped element $eid successfully." else "Failed tapping element $eid."
                } else {
                    val x = args.optDouble("x", 0.5).toFloat()
                    val y = args.optDouble("y", 0.5).toFloat()
                    val ok = ScreenAutomation.tapPoint(x, y)
                    if (ok) "Tapped coordinate ($x, $y) successfully." else "Failed tapping coordinate ($x, $y)."
                }
            }

            "type" -> {
                val text = args.optString("text")
                val clearFirst = args.optBoolean("clearFirst", true)
                val res = ScreenAutomation.typeTextUnique(text, clearFirst)
                if (res.success) "Typed '$text' (${res.via})." else "Failed typing: ${res.detail}"
            }

            "scroll" -> {
                val dir = args.optString("direction", "down")
                val times = args.optInt("times", 1)
                val moved = ScreenAutomation.scroll(dir, times)
                "Scrolled $dir $moved time(s)."
            }

            "swipe" -> {
                val dir = args.optString("direction", "up")
                val ok = ScreenAutomation.swipe(dir)
                if (ok) "Swiped $dir successfully." else "Failed to swipe $dir."
            }

            "open_app" -> {
                val appName = args.optString("name")
                val pkg = ScreenAutomation.launch(context, appName)
                if (pkg != null) "Launched $appName ($pkg)." else "Could not launch $appName."
            }

            "get_screen_outline" -> {
                val max = args.optInt("maxElements", 60)
                val outline = ScreenAutomation.describe(max)
                outline.ifEmpty { "Screen outline is empty or inaccessible." }
            }

            "run_command" -> {
                val cmd = args.optString("command")
                val outcome = CommandRouter.handle(context, cmd)
                if (outcome.handled) outcome.message else "Command not handled locally: $cmd"
            }

            "global_action" -> {
                val action = args.optString("action", "back")
                val ok = ScreenAutomation.global(action)
                if (ok) "Triggered $action successfully." else "Failed triggering $action."
            }

            else -> "Unknown tool: $name"
        }
    }

    private fun makeTool(name: String, desc: String, schema: JSONObject): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", desc)
            put("inputSchema", schema)
        }
    }

    private fun formatResponse(id: Any, result: JSONObject): String {
        return JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }.toString()
    }

    private fun formatError(id: Any, code: Int, message: String): String {
        return JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("error", JSONObject().apply {
                put("code", code)
                put("message", message)
            })
        }.toString()
    }
}
