/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.config

import com.fasterxml.jackson.databind.JsonNode
import ratpack.config.internal.DefaultConfigDataSpec
import ratpack.server.ServerConfig
import ratpack.server.ServerEnvironment

class ConfigDataUsageSpec extends BaseConfigSpec {
  def "properly loads ServerConfig defaults"() {
    when:
    def serverConfig = ConfigData.of().build().get(ServerConfig)

    then:
    !serverConfig.hasBaseDir
    serverConfig.port == ServerConfig.DEFAULT_PORT
    !serverConfig.address
    !serverConfig.development
    serverConfig.threads == ServerConfig.DEFAULT_THREADS
    !serverConfig.publicAddress
    serverConfig.maxContentLength == ServerConfig.DEFAULT_MAX_CONTENT_LENGTH
    !serverConfig.timeResponses
    !serverConfig.compressResponses
    serverConfig.compressionMinSize == ServerConfig.DEFAULT_COMPRESSION_MIN_SIZE
    serverConfig.compressionMimeTypeWhiteList == [] as Set
    serverConfig.compressionMimeTypeBlackList == [] as Set
    !serverConfig.SSLContext
  }

  def "can combine configuration from multiple sources"() {
    def jsonFile = tempFolder.newFile("file.json").toPath()
    jsonFile.text = '{"port": 8080}'
    def propsFile = tempFolder.newFile("file.properties").toPath()
    propsFile.text = 'development=true'
    def yamlFile = tempFolder.newFile("file.yaml").toPath()
    yamlFile.text = 'publicAddress: http://localhost:8080'
    def properties = new Properties()
    properties.setProperty("ratpack.threads", "3")
    def envData = [RATPACK_ADDRESS: "localhost"]

    when:
    def serverConfig = new DefaultConfigDataSpec(new ServerEnvironment(envData, properties)).json(jsonFile).yaml(yamlFile).props(propsFile).env().sysProps().build().get(ServerConfig)

    then:
    serverConfig.port == 8080
    serverConfig.address == InetAddress.getByName("localhost")
    serverConfig.development
    serverConfig.threads == 3
    serverConfig.publicAddress == URI.create("http://localhost:8080")
    serverConfig.maxContentLength == ServerConfig.DEFAULT_MAX_CONTENT_LENGTH
  }

  def "can get objects from subpaths"() {
    def yamlFile = tempFolder.newFile("file.yaml").toPath()
    yamlFile.text = """
    |server:
    |    port: 7654
    |db:
    |    jdbcUrl: "jdbc:h2:mem:"
    |""".stripMargin()
    when:
    def config = ConfigData.of().yaml(yamlFile).build()
    def serverConfig = config.get("/server", ServerConfig)
    def dbConfig = config.get("/db", TestDatabaseConfig)

    then:
    serverConfig.port == 7654
    dbConfig.jdbcUrl == "jdbc:h2:mem:"
  }

  def "when a value is present in multiple sources, the last source wins"() {
    def jsonFile = tempFolder.newFile("file.json").toPath()
    jsonFile.text = '{"port": 123}'
    def propsFile = tempFolder.newFile("file.properties").toPath()
    propsFile.text = 'port=345'
    def yamlFile = tempFolder.newFile("file.yaml").toPath()
    yamlFile.text = 'port: 234'
    def properties = new Properties()
    properties.setProperty("ratpack.port", "567")
    def envData = [RATPACK_PORT: "456"]

    when:
    def serverConfig = new DefaultConfigDataSpec(new ServerEnvironment(envData, properties)).json(jsonFile).yaml(yamlFile).props(propsFile).env().sysProps().build().get(ServerConfig)

    then:
    serverConfig.port == 567
  }

  def "can get raw data as node structure"() {
    def envData = [:]
    def properties = new Properties()
    properties.setProperty("ratpack.server.port", "6543")
    def yamlFile = tempFolder.newFile("file.yaml").toPath()
    yamlFile.text = """
    |server:
    |    port: 7654
    |db:
    |    jdbcUrl: "jdbc:h2:mem:"
    |""".stripMargin()
    when:
    def config = new DefaultConfigDataSpec(new ServerEnvironment(envData, properties)).yaml(yamlFile).sysProps().build()
    def node = config.get(JsonNode)

    then:
    node.toString() == '{"server":{"port":"6543"},"db":{"jdbcUrl":"jdbc:h2:mem:"}}'
  }
}
