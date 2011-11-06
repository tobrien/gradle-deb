/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests.fixtures

import java.security.Principal
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.junit.rules.MethodRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.mortbay.jetty.Handler
import org.mortbay.jetty.Request
import org.mortbay.jetty.Server
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.mortbay.jetty.handler.*
import org.mortbay.jetty.security.*
import org.mortbay.jetty.HttpHeaders
import org.mortbay.jetty.MimeTypes
import org.mortbay.jetty.HttpStatus

class HttpServer implements MethodRule {
    private Logger logger = LoggerFactory.getLogger(HttpServer.class)
    private final Server server = new Server(0)
    private final HandlerCollection collection = new HandlerCollection()
    private Throwable failure
    private TestUserRealm realm

    def HttpServer() {
        HandlerCollection handlers = new HandlerCollection()
        handlers.addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                println("handling http request: $request.method $target")
            }
        })
        handlers.addHandler(collection)
        handlers.addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (request.handled) {
                    return
                }
                failure = new AssertionError("Received unexpected ${request.method} request to ${target}.")
                logger.error(failure.message)
                response.sendError(404, "'$target' does not exist")
            }
        })
        server.setHandler(handlers)
    }

    void start() {
        server.start()
    }

    void stop() {
        server.stop()
    }

    void resetExpectations() {
        collection.setHandlers()
    }

    Statement apply(Statement base, FrameworkMethod method, Object target) {
        return new Statement() {
            @Override
            void evaluate() {
                try {
                    base.evaluate()
                } finally {
                    stop()
                }
                if (failure != null) {
                    throw failure
                }
            }
        }
    }

    /**
     * Adds a given file at the given URL. The source file can be either a file or a directory.
     */
    void allowGet(String path, File srcFile) {
        allow(path, true, ['GET', 'HEAD'], fileHandler(path, srcFile))
    }

    private AbstractHandler fileHandler(String path, File srcFile) {
        return new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                def file
                if (request.pathInfo == path) {
                    file = srcFile
                } else {
                    def relativePath = request.pathInfo.substring(path.length() + 1)
                    file = new File(srcFile, relativePath)
                }
                if (!file.isFile()) {
                    response.sendError(404, "'$target' does not exist")
                    return
                }
                response.setDateHeader(HttpHeaders.LAST_MODIFIED, file.lastModified())
                response.setContentLength((int)file.length())
                response.setContentType(new MimeTypes().getMimeByExtension(file.name).toString())
                response.outputStream.bytes = file.bytes
            }
        }
    }

    /**
     * Adds a broken resource at the given URL.
     */
    void addBroken(String path) {
        allow(path, true, null, new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                response.sendError(500, "broken")
            }
        })
    }

    /**
     * Allows one GET request for the given URL, which return 404 status code
     */
    void expectGetMissing(String path) {
        expect(path, false, ['GET'], new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                response.sendError(404, "not found")
            }
        })
    }


    /**
     * Allows one HEAD request for the given URL, which return 404 status code
     */
    void expectHeadMissing(String path) {
        expect(path, false, ['HEAD'], new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                response.sendError(404, "not found")
            }
        })
    }

    /**
     * Allows one HEAD request for the given URL.
     */
    void expectHead(String path, File srcFile) {
        expect(path, false, ['HEAD'], fileHandler(path, srcFile))
    }

    /**
     * Allows one GET request for the given URL. Reads the request content from the given file.
     */
    void expectGet(String path, File srcFile) {
        expect(path, false, ['GET'], fileHandler(path, srcFile))
    }

    /**
     * Allows one GET request for the given URL, with the given credentials. Reads the request content from the given file.
     */
    void expectGet(String path, String username, String password, File srcFile) {
        expect(path, false, ['GET'], withAuthentication(path, username, password, fileHandler(path, srcFile)))
    }

    /**
     * Allows one GET request for the given URL, returning an apache-compatible directory listing with the given File names.
     */
    void expectGetDirectoryListing(String path, File directory) {
        def directoryListing = ""
        for (String fileName: directory.list()) {
            directoryListing += "<a href=\"$fileName\">$fileName</a>"
        }
        expect(path, false, ['GET'], stringHandler(path, directoryListing))
    }

    private AbstractHandler stringHandler(String path, String content) {
        return new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                response.setContentLength(content.length())
                response.setContentType("text/plain")
                response.outputStream.bytes = content.bytes
            }
        }
    }

    /**
     * Allows one PUT request for the given URL. Writes the request content to the given file.
     */
    void expectPut(String path, File destFile, int statusCode = HttpStatus.ORDINAL_200_OK) {
        expect(path, false, ['PUT'], new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                destFile.bytes = request.inputStream.bytes
                response.setStatus(statusCode)
            }
        })
    }

    /**
     * Allows one PUT request for the given URL, with the given credentials. Writes the request content to the given file.
     */
    void expectPut(String path, String username, String password, File destFile) {
        expect(path, false, ['PUT'], withAuthentication(path, username, password, new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (request.remoteUser != username) {
                    response.sendError(500, 'unexpected username')
                    return
                }
                destFile.bytes = request.inputStream.bytes
            }
        }))
    }

    private Handler withAuthentication(String path, String username, String password, Handler handler) {
        if (realm != null) {
            assert realm.username == username
            assert realm.password == password
        } else {
            realm = new TestUserRealm()
            realm.username = username
            realm.password = password
            def constraint = new Constraint()
            constraint.name = Constraint.__BASIC_AUTH
            constraint.authenticate = true
            constraint.roles = ['*'] as String[]
            def constraintMapping = new ConstraintMapping()
            constraintMapping.pathSpec = path
            constraintMapping.constraint = constraint
            def securityHandler = new SecurityHandler()
            securityHandler.userRealm = realm
            securityHandler.constraintMappings = [constraintMapping] as ConstraintMapping[]
            securityHandler.authenticator = new BasicAuthenticator()
            collection.addHandler(securityHandler)
        }

        return new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (request.remoteUser != username) {
                    response.sendError(500, 'unexpected username')
                    return
                }
                handler.handle(target, request, response, dispatch)
            }
        }
    }

    private void expect(String path, boolean recursive, Collection<String> methods, Handler handler) {
        boolean run
        add(path, recursive, methods, new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (run) {
                    return
                }
                run = true
                handler.handle(target, request, response, dispatch)
                request.handled = true
            }
        })
    }

    private void allow(String path, boolean recursive, Collection<String> methods, Handler handler) {
        add(path, recursive, methods, new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                handler.handle(target, request, response, dispatch)
                request.handled = true
            }
        })
    }

    private void add(String path, boolean recursive, Collection<String> methods, Handler handler) {
        assert path.startsWith('/')
//        assert path == '/' || !path.endsWith('/')
        def prefix = path == '/' ? '/' : path + '/'
        collection.addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (methods != null && !methods.contains(request.method)) {
                    return
                }
                boolean match = request.pathInfo == path || (recursive && request.pathInfo.startsWith(prefix))
                if (match && !request.handled) {
                    handler.handle(target, request, response, dispatch)
                }
            }
        })
    }

    int getPort() {
        return server.connectors[0].localPort
    }

    static class TestUserRealm implements UserRealm {
        String username
        String password

        Principal authenticate(String username, Object credentials, Request request) {
            if (username == this.username && password == credentials) {
                return getPrincipal(username)
            }
            return null
        }

        String getName() {
            return "test"
        }

        Principal getPrincipal(String username) {
            return new Principal() {
                String getName() {
                    return username
                }
            }
        }

        boolean reauthenticate(Principal user) {
            return false
        }

        boolean isUserInRole(Principal user, String role) {
            return false
        }

        void disassociate(Principal user) {
        }

        Principal pushRole(Principal user, String role) {
            return user
        }

        Principal popRole(Principal user) {
            return user
        }

        void logout(Principal user) {
        }

    }
}
