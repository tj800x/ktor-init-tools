/*
 * Copyright 2018 JetBrains s.r.o.
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
 *
 */

package io.ktor.start.intellij

import com.intellij.ide.util.projectWizard.*
import com.intellij.openapi.ui.*
import com.intellij.ui.*
import io.ktor.start.*
import io.ktor.start.features.*
import io.ktor.start.intellij.util.*
import io.ktor.start.util.*
import java.awt.*
import java.net.*
import javax.swing.*
import javax.swing.tree.*

// https://github.com/JetBrains/intellij-community/blob/master/java/idea-ui/src/com/intellij/ide/util/newProjectWizard/AddSupportForFrameworksPanel.java
// https://github.com/JetBrains/intellij-community/blob/master/java/idea-ui/src/com/intellij/ide/util/newProjectWizard/FrameworksTree.java
// Splitter
// CheckBoxTree
// FrameworksTree
class KtorModuleWizardStep(val config: KtorModuleConfig) : ModuleWizardStep() {
    override fun updateDataModel() {
        config.projectType = projectTypeCB.selectedItem as ProjectType
        config.featuresToInstall = featuresToCheckbox.keys.filter { it.selected }
        config.ktorVersion = versionCB.selected
        config.wrapper = wrapperCheckBox.isSelected
        config.engine = engineCB.selected
    }

    lateinit var projectTypeCB: JComboBox<ProjectType>
    lateinit var engineCB: JComboBox<KtorEngine>
    lateinit var versionCB: JComboBox<SemVer>
    lateinit var wrapperCheckBox: JCheckBox
    val featuresToCheckbox = LinkedHashMap<Feature, CheckedTreeNode>()
    val panel = JPanel().apply {
        val description = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = IdeBorderFactory.createBorder()
        }

        fun showFeatureDocumentation(feature: Feature) {
            description.removeAll()
            description.add(JLabel(feature.description, SwingConstants.LEFT))
            val doc = feature.documentation
            if (doc != null) {
                description.add(Link(doc, URL(doc)))
            }
            description.doLayout()
            description.repaint()
        }

        val featureServerList = object : FeatureCheckboxList(ALL_SERVER_FEATURES) {
            override fun onSelected(feature: Feature, node: CheckedTreeNode) {
                showFeatureDocumentation(feature)
            }

            override fun onChanged(feature: Feature, node: CheckedTreeNode) {
                updatedFeature(feature)
            }
        }

        val featureClientList = object : FeatureCheckboxList(ALL_CLIENT_FEATURES) {
            override fun onSelected(feature: Feature, node: CheckedTreeNode) {
                showFeatureDocumentation(feature)
            }

            override fun onChanged(feature: Feature, node: CheckedTreeNode) {
                updatedFeature(feature)
            }
        }

        featuresToCheckbox += featureServerList.featuresToCheckbox
        featuresToCheckbox += featureClientList.featuresToCheckbox

        this.layout = BorderLayout(0, 0)

        add(table {
            tr(
                policy = TdSize.FIXED,
                maxHeight = 26,
                fill = TdFill.NONE,
                align = TdAlign.CENTER_LEFT
            ) {
                td(JLabel("Project:"))
                td(JComboBox(ProjectType.values()).apply { projectTypeCB = this })
                td(JCheckBox("Wrapper", true).apply { wrapperCheckBox = this })
                td(JLabel("Using:"))
                td(JComboBox(KtorEngine.values()).apply { engineCB = this })
                td(JLabel("Ktor Version:"))
                td(JComboBox(Versions.ALL).apply { versionCB = this })
            }
        }, BorderLayout.NORTH)

        add(Splitter(true, 0.8f, 0.2f, 0.8f).apply {
            this.firstComponent = table {
                tr(
                    policy = TdSize.FIXED,
                    minHeight = 24,
                    maxHeight = 24,
                    fill = TdFill.NONE,
                    align = TdAlign.CENTER_LEFT
                ) {
                    td(JLabel("Server:"))
                    td(JLabel("Client:"))
                }
                tr {
                    td(featureServerList.scrollVertical())
                    td(featureClientList.scrollVertical())
                }
            }
            this.secondComponent = description
        }, BorderLayout.CENTER)
    }

    var Feature.selected: Boolean
        get() = featuresToCheckbox[this]?.isChecked ?: false
        set(value) {
            featuresToCheckbox[this]?.isChecked = value
        }

    //var Feature.indeterminate : Boolean
    //    get() = featuresToCheckbox[this]?. ?: false
    //    set(value) {
    //        featuresToCheckbox[this]?.isSelected = value
    //    }

    fun updatedFeature(feature: Feature) {
        if (feature.selected) {
            for (feat in feature.featureDeps) {
                feat.selected = true
            }
        }
    }

    override fun getComponent() = panel
}

abstract class FeatureCheckboxList(val features: List<Feature>) : CheckboxTree(
    object : CheckboxTree.CheckboxTreeCellRenderer() {
        override fun customizeRenderer(
            tree: JTree?,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            if (value is CheckedTreeNode) {
                val feature = value.userObject
                if (feature is Feature) {
                    textRenderer.append(feature.title)
                    checkbox.isVisible = true
                }
            }
        }
    },
    CheckedTreeNode()
) {
    val CheckedTreeNode?.feature: Feature? get() = this?.userObject as? Feature?

    val featuresToCheckbox = LinkedHashMap<Feature, CheckedTreeNode>()
    val root = (this.model as DefaultTreeModel).root as CheckedTreeNode
    init {
        this.model = object : DefaultTreeModel(root) {
            override fun valueForPathChanged(path: TreePath, newValue: Any) {
                super.valueForPathChanged(path, newValue)
                val node = path.lastPathComponent as CheckedTreeNode
                val feature = node.feature
                if (feature != null) {
                    onChanged(feature, node)
                }
            }
        }
    }



    init {
        for (feature in features) {
            root.add(CheckedTreeNode(feature).apply { isChecked = false; featuresToCheckbox[feature] = this })
        }
        (this.model as DefaultTreeModel).reload(root)

        addTreeSelectionListener { e ->
            val node = (e.newLeadSelectionPath.lastPathComponent as? CheckedTreeNode)
            val feature = node?.userObject as? Feature?

            if (node != null && feature != null) {
                onSelected(feature, node)
            }
        }
    }

    abstract fun onSelected(feature: Feature, node: CheckedTreeNode)
    open fun onChanged(feature: Feature, node: CheckedTreeNode) {
    }
}
