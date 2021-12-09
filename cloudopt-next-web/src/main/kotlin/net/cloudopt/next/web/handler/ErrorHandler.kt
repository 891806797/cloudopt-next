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
package net.cloudopt.next.web.handler

import net.cloudopt.next.web.Resource

abstract class ErrorHandler : Resource() {

    /**
     * Used to catch exceptions in context.
     * @param statusCode Int the HTTP status code of the response
     * @param throwable Throwable the throwable used when signalling failure
     */
    abstract suspend fun handle(statusCode: Int, throwable: Throwable?)

}


