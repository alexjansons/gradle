/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.plugins.quality.pmd

import org.gradle.util.internal.VersionNumber
import org.junit.Assume

import static org.hamcrest.CoreMatchers.containsString

class PmdCustomJavaRuleIntegrationTest extends AbstractPmdPluginVersionIntegrationTest {

    def "can use a custom rule implemented in Java"() {
        given:
        Assume.assumeTrue(versionNumber >= VersionNumber.parse("7.0.0"))
        settingsFile << "include 'rules'"
        file("rules/build.gradle") << """
            plugins {
                id "java-library"
            }

            ${mavenCentralRepository()}

            dependencies {
                compileOnly "net.sourceforge.pmd:pmd-java:$version"
            }
        """
        file("rules/src/main/java/org/gradle/rules/NoMethodsRule.java") << """
            package org.gradle.rules;

            import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
            import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;

            public class NoMethodsRule extends AbstractJavaRule {
                @Override
                public Object visit(ASTMethodDeclaration node, Object data) {
                    asCtx(data).addViolation(node);
                    return data;
                }
            }
        """
        file("rules/src/main/resources/org/gradle/rules/ruleset.xml") << """
            <ruleset name="custom"
                xmlns="http://pmd.sourceforge.net/ruleset/2.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://pmd.sourceforge.net/ruleset/2.0.0 https://pmd.sourceforge.io/ruleset_2_0_0.xsd">
                <description>Custom rule set</description>
                <rule name="NoMethods" language="java" message="Methods are not allowed" class="org.gradle.rules.NoMethodsRule"/>
            </ruleset>
        """
        buildFile << """
            plugins {
                id "java"
                id "pmd"
            }

            ${mavenCentralRepository()}

            dependencies {
                // Declaring a dependency disables the default PMD dependencies
                ${calculateDefaultDependencyNotation().collect { "pmd \"$it\"" }.join('\n')}
                pmd project(":rules")
            }

            pmd {
                toolVersion = '$version'
                ruleSets = ["org/gradle/rules/ruleset.xml"]
            }
        """
        file("src/main/java/org/gradle/Class1.java") << "package org.gradle; class Class1 { void foo() {} }"

        expect:
        fails("pmdMain")
        failure.assertThatCause(containsString("1 PMD rule violations were found."))
        file("build/reports/pmd/main.xml").assertContents(containsString("Methods are not allowed"))
    }
}
