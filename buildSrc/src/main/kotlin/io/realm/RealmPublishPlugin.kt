/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin

import Realm
import io.github.gradlenexus.publishplugin.NexusPublishExtension
import io.github.gradlenexus.publishplugin.NexusPublishPlugin
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import java.io.File
import java.time.Duration
import java.net.URI

open class PomOptions {
    open var name: String = ""
    open var description: String = ""
}

open class RealmPublishExtensions {
    open var pom: PomOptions = PomOptions()
    open fun pom(action: Action<PomOptions>) {
        action.execute(pom)
    }
}

fun getPropertyValue(project: Project, propertyName: String, defaultValue: String = ""): String {
    if (project.hasProperty(propertyName)) {
        return project.property(propertyName) as String
    }
    val systemValue: String? = System.getenv(propertyName)
    return systemValue ?: defaultValue
}

fun hasProperty(project: Project, propertyName: String): Boolean {
    val systemProp: String? = System.getenv(propertyName)
    val projectProp: Boolean = project.hasProperty(propertyName)
    return projectProp || (systemProp != null && systemProp.isNotEmpty())
}

class RealmPublishPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val signBuild: Boolean = hasProperty(project,"signBuild")
        configureSignedBuild(signBuild, this)
    }

    private fun configureTestRepository(project: Project) {
        val relativePathToTestRepository: String = getPropertyValue(project, "testRepository")
        val testRepository = File(project.rootProject.rootDir.absolutePath + File.separator + relativePathToTestRepository.replace("/", File.separator))
        if (relativePathToTestRepository.isNotEmpty()) {
            project.extensions.getByType<PublishingExtension>().apply {
                repositories {
                    maven {
                        name = "Test"
                        url = testRepository.toURI()
                    }
                }
            }
        }
    }

    private fun configureGitHubPackagesRepository(project: Project) {
        val githubActor = getPropertyValue(project, "GITHUB_ACTOR")
        val githubToken = getPropertyValue(project, "GITHUB_TOKEN")
        
        if (githubActor.isNotEmpty() && githubToken.isNotEmpty()) {
            project.extensions.getByType<PublishingExtension>().apply {
                repositories {
                    maven {
                        name = "GitHubPackages"
                        url = URI("https://maven.pkg.github.com/Black-Bricks/realm-kotlin")
                        credentials {
                            username = githubActor
                            password = githubToken
                        }
                    }
                }
            }
        }
    }

    private fun configureSignedBuild(signBuild: Boolean, project: Project) {
        val isRootProject: Boolean = (project == project.rootProject)
        if (isRootProject) {
            configureRootProject(project)
        } else {
            configureSubProject(project, signBuild)
            configureTestRepository(project)
            configureGitHubPackagesRepository(project)
        }
    }

    private fun configureSubProject(project: Project, signBuild: Boolean) {
        val keyId = "1F48C9B0"
        val ringFile: String = getPropertyValue(project,"signSecretRingFileKotlin").replace('#', '\n')
        val password: String = getPropertyValue(project, "signPasswordKotlin")

        with(project) {
            plugins.apply(SigningPlugin::class.java)
            plugins.apply(MavenPublishPlugin::class.java)
            extensions.create<RealmPublishExtensions>("realmPublish")

            afterEvaluate {
                project.extensions.findByType<RealmPublishExtensions>()?.run {
                    configurePom(project, pom)
                }
            }

            extensions.getByType<SigningExtension>().apply {
                isRequired = signBuild
                useInMemoryPgpKeys(keyId, ringFile, password)
                sign(project.extensions.getByType<PublishingExtension>().publications)
            }
        }
    }

    private fun configureRootProject(project: Project) {

    }

    private fun configurePom(project: Project, options: PomOptions) {
        project.extensions.getByType<PublishingExtension>().apply {
            publications.withType<MavenPublication>().all {
                pom {
                    name.set(options.name)
                    description.set(options.description)
                    url.set(Realm.projectUrl)
                    licenses {
                        license {
                            name.set(Realm.License.name)
                            url.set(Realm.License.url)
                        }
                    }
                    issueManagement {
                        system.set(Realm.IssueManagement.system)
                        url.set(Realm.IssueManagement.url)
                    }
                    scm {
                        connection.set(Realm.SCM.connection)
                        developerConnection.set(Realm.SCM.developerConnection)
                        url.set(Realm.SCM.url)
                    }
                    developers {
                        developers {
                            developer {
                                name.set(Realm.Developer.name)
                                email.set(Realm.Developer.email)
                                organization.set(Realm.Developer.organization)
                                organizationUrl.set(Realm.Developer.organizationUrl)
                            }
                        }
                    }
                }
            }
        }
    }
}
