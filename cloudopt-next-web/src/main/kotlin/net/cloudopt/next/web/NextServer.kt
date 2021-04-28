/*
 * Copyright 2017-2021 Cloudopt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.cloudopt.next.web

import io.vertx.core.http.HttpMethod
import net.cloudopt.next.core.*
import net.cloudopt.next.json.JsonProvider
import net.cloudopt.next.json.Jsoner
import net.cloudopt.next.logging.test.Logger
import net.cloudopt.next.web.annotation.Blocking
import net.cloudopt.next.web.config.WebConfigBean
import net.cloudopt.next.web.annotation.AutoHandler
import net.cloudopt.next.web.handler.ErrorHandler
import net.cloudopt.next.web.handler.Handler
import net.cloudopt.next.web.render.Render
import net.cloudopt.next.web.render.RenderFactory
import net.cloudopt.next.web.annotation.*
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation

object NextServer {

    open var webConfig: WebConfigBean = ConfigManager.configMap.toObject(WebConfigBean::class)

    private var verticleID = "net.cloudopt.next.web"

    val logger = Logger.getLogger(NextServer::class)

    private val plugins = arrayListOf<Plugin>()

    private val resources: MutableList<KClass<Resource>> = arrayListOf()

    open val sockJSes: MutableList<KClass<SockJSResource>> = arrayListOf()

    open val webSockets: MutableList<KClass<WebSocketResource>> = arrayListOf()

    open val handlers = arrayListOf<Handler>()

    open val interceptors = mutableMapOf<String, MutableList<KClass<out Interceptor>>>()

    open val beforeRouteHandlersTable = mutableMapOf<String, MutableMap<HttpMethod, Array<Annotation>>>()

    open val afterRouteHandlersTable = mutableMapOf<String, MutableMap<HttpMethod, Array<Annotation>>>()

    open val resourceTable = arrayListOf<ResourceTable>()

    open var packageName = ""

    open var errorHandler: KClass<ErrorHandler> =
        Classer.loadClass(webConfig.errorHandler) as KClass<ErrorHandler>

    init {
        /**
         * Set json provider
         */
        Jsoner.jsonProvider = Classer.loadClass(webConfig.jsonProvider).createInstance() as JsonProvider
    }

    /**
     * Scan by annotation and register as a route.
     */
    private fun scan() {

        //Set log color
        Logger.configuration.color = webConfig.logColor

        //Scan cloudopt handler
        Classer.scanPackageByAnnotation("net.cloudopt.next", true, AutoHandler::class)
            .forEach { clazz ->
                handlers.add(clazz.createInstance() as Handler)
            }

        packageName = webConfig.packageName.ifBlank {
            throw RuntimeException("Package name must not be null!")
        }

        //Scan custom handler
        Classer.scanPackageByAnnotation(packageName, true, AutoHandler::class)
            .forEach { clazz ->
                handlers.add(clazz.createInstance() as Handler)
            }

        //Scan sockJS
        Classer.scanPackageByAnnotation(packageName, true, SocketJS::class)
            .forEach { clazz ->
                sockJSes.add(clazz as KClass<SockJSResource>)
            }

        //Scan webSocket
        Classer.scanPackageByAnnotation(packageName, true, WebSocket::class)
            .forEach { clazz ->
                webSockets.add(clazz as KClass<WebSocketResource>)
            }

        //Scan resources
        Classer.scanPackageByAnnotation(packageName, true, API::class)
            .forEach { clazz ->
                resources.add(clazz as KClass<Resource>)
            }

        for (clazz in resources) {

            // Get api annotation
            val apiAnnotation: API? = clazz.findAnnotation<API>()

            //Register interceptor
            apiAnnotation?.interceptor?.forEach { inClass ->
                var url = apiAnnotation.value
                if (url.endsWith("/")) {
                    url = "$url*"
                } else {
                    url = "$url/*"
                }
                if (interceptors.containsKey(url)) {
                    interceptors[url]!!.add(inClass)
                } else {
                    interceptors[url] = mutableListOf(inClass)
                }

            }

            //Get methods annotation
            val functions = clazz.functions

            functions.forEach { function ->

                val functionsAnnotations = function.annotations

                var resourceUrl = ""

                var httpMethod: HttpMethod = HttpMethod.GET

                var blocking = false

                functionsAnnotations.forEach { functionAnnotation ->
                    when (functionAnnotation) {
                        is GET -> {
                            resourceUrl = "${apiAnnotation?.value}${functionAnnotation.value}"
                            httpMethod = HttpMethod(functionAnnotation.method)
                        }
                        is POST -> {
                            resourceUrl = "${apiAnnotation?.value}${functionAnnotation.value}"
                            httpMethod = HttpMethod(functionAnnotation.method)
                        }
                        is PUT -> {
                            resourceUrl = "${apiAnnotation?.value}${functionAnnotation.value}"
                            httpMethod = HttpMethod(functionAnnotation.method)
                        }
                        is DELETE -> {
                            resourceUrl = "${apiAnnotation?.value}${functionAnnotation.value}"
                            httpMethod = HttpMethod(functionAnnotation.method)
                        }
                        is PATCH -> {
                            resourceUrl = "${apiAnnotation?.value}${functionAnnotation.value}"
                            httpMethod = HttpMethod(functionAnnotation.method)
                        }
                        is net.cloudopt.next.web.annotation.HttpMethod -> {
                            resourceUrl = "${apiAnnotation?.value}${functionAnnotation.value}"
                            httpMethod = HttpMethod(functionAnnotation.method)
                        }
                        is Blocking -> {
                            blocking = true
                        }
                    }

                    /**
                     * If it is an annotation with @Before annotation, it is automatically added to the list.
                     */
                    if (resourceUrl.isNotBlank() && functionAnnotation.annotationClass.hasAnnotation<Before>()) {

                        if (beforeRouteHandlersTable.containsKey(resourceUrl)) {
                            if (beforeRouteHandlersTable[resourceUrl]?.containsKey(httpMethod) == true){
                                beforeRouteHandlersTable[resourceUrl]?.get(httpMethod)?.plus(functionAnnotation)
                            }else{
                                beforeRouteHandlersTable[resourceUrl]?.set(httpMethod, arrayOf(functionAnnotation))
                            }
                        } else {
                            val temp = mutableMapOf<HttpMethod, Array<Annotation>>()
                            temp[httpMethod] = arrayOf(functionAnnotation)
                            beforeRouteHandlersTable[resourceUrl] = temp
                        }
                    }

                    if (resourceUrl.isNotBlank() && functionAnnotation.annotationClass.hasAnnotation<After>()) {

                        if (afterRouteHandlersTable.containsKey(resourceUrl)) {
                            if (afterRouteHandlersTable[resourceUrl]?.containsKey(httpMethod) == true){
                                afterRouteHandlersTable[resourceUrl]?.get(httpMethod)?.plus(functionAnnotation)
                            }else{
                                afterRouteHandlersTable[resourceUrl]?.set(httpMethod, arrayOf(functionAnnotation))
                            }
                        } else {
                            val temp = mutableMapOf<HttpMethod, Array<Annotation>>()
                            temp[httpMethod] = arrayOf(functionAnnotation)
                            afterRouteHandlersTable[resourceUrl] = temp
                        }
                    }

                }

                if (resourceUrl.isNotBlank()) {
                    val r = ResourceTable(
                        resourceUrl,
                        httpMethod,
                        clazz,
                        function.name,
                        blocking,
                        function,
                        function.typeParameters
                    )
                    resourceTable.add(r)
                }
            }


        }
    }

    /**
     * Get package path via class.
     * @param clazz Class<*>
     */
    @JvmStatic
    fun run(clazz: Class<*>) {
        webConfig.packageName = clazz.`package`.name
        run()
    }

    /**
     * Get package path via class.
     * @param clazz Class<*>
     */
    @JvmStatic
    fun run(clazz: KClass<*>) {
        webConfig.packageName = clazz.java.`package`.name
        run()
    }

    /**
     * Get package path by package's name.
     * @param pageName package's name
     */
    @JvmStatic
    fun run(pageName: String) {
        webConfig.packageName = pageName
        run()
    }

    /**
     * Get package path by package's name.
     */
    @JvmStatic
    fun run() {
        scan()
        /**
         * Print banner
         */
        Banner.print()
        startPlugins()
        Worker.deploy("net.cloudopt.next.web.NextServerVerticle", workerPoolName = "net.cloudopt.next.http")
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                NextServer.stop()
            }
        })
    }

    /**
     * Add custom render.
     * @see net.cloudopt.next.web.render.Render
     * @param extension render's name
     * @param render Render object
     * @return CloudoptServer
     */
    @JvmStatic
    fun addRender(extension: String, render: Render): NextServer {
        RenderFactory.add(extension, render)
        return this
    }

    /**
     * Set the default render.
     * @param name render name
     * @return CloudoptServer
     */
    @JvmStatic
    fun setDefaultRender(name: String): NextServer {
        RenderFactory.setDefaultRender(name)
        return this
    }

    /**
     * Add the plugins that need to be started and the plugins will start first after the server starts.
     * @see net.cloudopt.next.web.Plugin
     * @param plugin Plugin object
     * @return CloudoptServer
     */
    @JvmStatic
    fun addPlugin(plugin: Plugin): NextServer {
        plugins.add(plugin)
        return this
    }

    /**
     * Add a handler that needs to be started and the handler will handle all requests.
     * @see net.cloudopt.next.web.handler.Handler
     * @param handler Handler
     * @return CloudoptServer
     */
    @JvmStatic
    fun addHandler(handler: Handler): NextServer {
        handlers.add(handler)
        return this
    }

    /**
     * Register all plugins
     */
    @JvmStatic
    fun startPlugins() {
        plugins.forEach { plugin ->
            if (plugin.start()) {
                logger.info("[PLUGIN] Registered plugin：" + plugin.javaClass.name)
            } else {
                logger.info("[PLUGIN] Started plugin was error：" + plugin.javaClass.name)
            }
        }
    }

    /**
     * Stop all plugins
     */
    @JvmStatic
    fun stopPlugins() {
        plugins.forEach { plugin ->
            if (!plugin.stop()) {
                logger.info("[PLUGIN] Stoped plugin was error：${plugin.javaClass.name}")
            }
        }
    }

    /**
     * Stop the the Vertx instance and release any resources held by it.
     * <p>
     * The instance cannot be used after it has been closed.
     * <p>
     * The actual close is asynchronous and may not complete until after the call has returned.
     */
    @JvmStatic
    fun stop() {
        stopPlugins()
        Worker.undeploy("net.cloudopt.next.web.CloudoptServerVerticle")
        Worker.close()
        logger.info("Next has exited.")
    }


}