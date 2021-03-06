/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package runtime.actionContainers

import java.io.File

import actionContainers.ResourceHelpers.readAsBase64
import common.WskActorSystem
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import spray.json._

@RunWith(classOf[JUnitRunner])
class PythonActionLoopContainerTests extends PythonActionContainerTests with WskActorSystem {

  override lazy val imageName = "actionloop-python-v3.7"

  override val testNoSource = TestConfig("", hasCodeStub = false)

  /** actionloop based image does not log init errors - return the error in the body */
  override lazy val initErrorsAreLogged = false

  def testArtifact(name: String): File = {
    new File(this.getClass.getClassLoader.getResource(name).toURI)
  }

  it should "run zipped Python action containing a virtual environment" in {
    val zippedPythonAction = testArtifact("python_virtualenv.zip")
    val code = readAsBase64(zippedPythonAction.toPath)

    withActionContainer() { c =>
      val (initCode, initRes) = c.init(initPayload(code))
      initCode should be(200)

      val (runCode, runRes) = c.run(runPayload(JsObject.empty))
      runCode should be(200)
      runRes.get.prettyPrint should include("\"agent\"")
    }
  }

  it should "run zipped Python action containing a virtual environment with non-standard entry point" in {
    val zippedPythonAction = testArtifact("python_virtualenv.zip")
    val code = readAsBase64(zippedPythonAction.toPath)

    withActionContainer() { c =>
      val (initCode, initRes) = c.init(initPayload(code, main = "naim"))
      initCode should be(200)

      val (runCode, runRes) = c.run(runPayload(JsObject.empty))
      runCode should be(200)
      runRes.get.prettyPrint should include("\"agent\"")
    }
  }

  it should "report error if zipped Python action has wrong main module name" in {
    val zippedPythonAction = testArtifact("python_virtualenv_invalid_main.zip")
    val code = readAsBase64(zippedPythonAction.toPath)

    val (out, err) = withActionContainer() { c =>
      val (initCode, initRes) = c.init(initPayload(code, main = "main"))
      initCode should be(502)

      if (!initErrorsAreLogged)
        initRes.get.fields.get("error").get.toString should include("Zip file does not include mandatory files")
    }

    if (initErrorsAreLogged)
      checkStreams(out, err, {
        case (o, e) =>
          o shouldBe empty
          e should include("Zip file does not include __main__.py")
      })
  }

  it should "report error if zipped Python action has invalid virtualenv directory" in {
    val zippedPythonAction = testArtifact("python_virtualenv_invalid_venv.zip")
    val code = readAsBase64(zippedPythonAction.toPath)

    val (out, err) = withActionContainer() { c =>
      val (initCode, initRes) = c.init(initPayload(code, main = "main"))
      initCode should be(502)

      if (!initErrorsAreLogged)
        initRes.get.fields.get("error").get.toString should include("Invalid virtualenv. Zip file does not include")
    }

    if (initErrorsAreLogged)
      checkStreams(out, err, {
        case (o, e) =>
          o shouldBe empty
          e should include("Zip file does not include /virtualenv/bin/")
      })
  }
}
