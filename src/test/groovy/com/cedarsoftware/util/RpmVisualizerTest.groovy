package com.cedarsoftware.util

import com.cedarsoftware.ncube.ApplicationID
import com.cedarsoftware.ncube.Axis
import com.cedarsoftware.ncube.AxisType
import com.cedarsoftware.ncube.AxisValueType
import com.cedarsoftware.ncube.GroovyExpression
import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.ncube.NCubeManager
import com.cedarsoftware.ncube.NCubeResourcePersister
import com.cedarsoftware.ncube.ReleaseStatus
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Test

import static com.cedarsoftware.util.RpmVisualizerConstants.*
import static com.cedarsoftware.util.VisualizerTestConstants.*

@CompileStatic
class RpmVisualizerTest
{
    static final String PATH_PREFIX = 'rpmvisualizer*//**//*'
    static final String DETAILS_LABEL_UTILIZED_SCOPE_WITH_TRAITS = 'Utilized scope with traits'
    static final String DETAILS_LABEL_UTILIZED_SCOPE_WITHOUT_TRAITS = 'Utilized scope with no traits'
    static final String DETAILS_LABEL_FIELDS = 'Fields</b>'
    static final String DETAILS_LABEL_FIELDS_AND_TRAITS = 'Fields and traits'
    static final String DETAILS_LABEL_CLASS_TRAITS = 'Class traits'
    static final String VALID_VALUES_FOR_FIELD_SENTENCE_CASE = 'Valid values for field '
    static final String VALID_VALUES_FOR_FIELD_LOWER_CASE = 'valid values for field '

    static final String defaultScopeDate = DATE_TIME_FORMAT.format(new Date())
    Map defaultRpmScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                           policyControlDate: defaultScopeDate,
                           quoteDate        : defaultScopeDate] as CaseInsensitiveMap

    RpmVisualizer visualizer
    Map topNodeScope
    ApplicationID appId
    Map returnMap
    Map scopeInfo //TODO remove
    RpmVisualizerInfo visInfo
    Set messages
    Map<Long, Map<String, Object>> nodes
    Map<Long, Map<String, Object>> edges
    Map<String, Object> selectedNode

    @Before
    void beforeTest(){
        appId = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'test.visualizer', ApplicationID.DEFAULT_VERSION, ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)
        visualizer = new RpmVisualizer()
        topNodeScope = new CaseInsensitiveMap()
        returnMap = null
        visInfo = null
        messages = null
        nodes = null
        edges = null
        selectedNode = null
        NCubeManager.NCubePersister = new NCubeResourcePersister(PATH_PREFIX)
    }

    @Test
    void testLoadGraph_checkVisInfo()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                             coverage         : 'FCoverage'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.class.Coverage'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        loadGraph(options)

        //Check visInfo
        assert 5 == visInfo.nodes.size()
        assert 4 == visInfo.edges.size()
        assert 4l == visInfo.maxLevel
        assert 4l == visInfo.edgeIdCounter
        assert 5l == visInfo.nodeIdCounter
        assert 3l == visInfo.defaultLevel
        assert 1l == visInfo.selectedNodeId
        assert '_ENUM' == visInfo.groupSuffix
        assert 'class' == visInfo.nodeLabel
        assert 'traits' == visInfo.cellValuesLabel

        Map allGroups =  [PRODUCT: 'Product', FORM: 'Form', RISK: 'Risk', COVERAGE: 'Coverage', CONTAINER: 'Container', DEDUCTIBLE: 'Deductible', LIMIT: 'Limit', RATE: 'Rate', RATEFACTOR: 'Rate Factor', PREMIUM: 'Premium', PARTY: 'Party', PLACE: 'Place', ROLE: 'Role', ROLEPLAYER: 'Role Player', UNSPECIFIED: 'Unspecified']
        assert allGroups == visInfo.allGroups
        assert allGroups.keySet() == visInfo.allGroupsKeys
        assert ['COVERAGE', 'RISK'] as Set == visInfo.availableGroupsAllLevels

        //Spot check typesToAddMap
        assert ['Coverage', 'Deductible', 'Limit', 'Premium', 'Rate', 'Ratefactor', 'Role'] == visInfo.typesToAddMap['Coverage']

        //Spot check the network overrides
        assert (visInfo.networkOverridesBasic.groups as Map).keySet().containsAll(allGroups.keySet())
        assert false == ((visInfo.networkOverridesFull.nodes as Map).shadow as Map).enabled
        assert (visInfo.networkOverridesSelectedNode.shapeProperties as Map).containsKey('borderDashes')
    }

    @Test
    void testLoadGraph_canLoadTargetAsRpmClass()
    {
        Map utilizedScope = new CaseInsensitiveMap()
        Map availableScope = defaultRpmScope + [coverage: 'CCCoverage']

        //Load graph
        String startCubeName = 'rpm.class.Coverage'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        loadGraph(options)
        Map node = nodes.values().find { Map node1 -> "${UNABLE_TO_LOAD}Location".toString() == node1.label }
        node = loadNodeDetails(node)
        checkNode('Location', 'Risk', UNABLE_TO_LOAD, 'Coverage points directly to Risk via field Location, but there is no risk named Location on Risk.', true)
        availableScope.Risk = 'Location'
        assert availableScope == node.availableScope
        assert utilizedScope == node.scope
    }

    @Test
    void testLoadGraph_checkNodeAndEdge_nonEPM()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap
        Map availableScope = new CaseInsensitiveMap(utilizedScope)

        //Load graph
        String startCubeName = 'rpm.class.partyrole.LossPrevention'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options)

        //Top level source node
        checkNode('partyrole.LossPrevention', 'partyrole.LossPrevention')
        assert null == node.fromFieldName
        assert 'UNSPECIFIED' == node.group
        assert '1' == node.level
        assert null == node.sourceCubeName
        assert null == node.sourceDescription
        assert null == node.typesToAdd
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope
        assert (node.details as String).contains("${DETAILS_LABEL_FIELDS}<pre><ul><li>roleRefCode</li><li>Parties</li></ul></pre>")

        //Edge from top level node to enum
        Map edge = edges.values().find { Map edge -> 'partyrole.LossPrevention' == edge.fromName && 'partyrole.BasePartyRole.Parties' == edge.toName}
        assert 'Parties' == edge.fromFieldName
        assert '2' == edge.level
        assert 'Parties' == edge.label
        assert "Field Parties cardinality ${V_MIN_CARDINALITY}:${V_MAX_CARDINALITY}".toString() == edge.title

        //Enum node under top level node
        String nodeTitle = "${VALID_VALUES_FOR_FIELD_SENTENCE_CASE}Parties on partyrole.LossPrevention"
        node = nodes.values().find {Map node1 ->  nodeTitle == node1.title}
        node = loadNodeDetails(node)
        checkEnumNode(nodeTitle, '', false)
        assert 'Parties' == node.fromFieldName
        assert 'PARTY_ENUM' == node.group
        assert '2' == node.level
        assert 'rpm.class.partyrole.LossPrevention' == node.sourceCubeName
        assert 'LossPrevention' == node.sourceDescription
        assert null == node.typesToAdd
        assert utilizedScope == node.scope
        availableScope.sourceFieldName = 'Parties'
        assert availableScope == node.availableScope
        assert (node.details as String).contains("${DETAILS_LABEL_FIELDS}<pre><ul><li>party.MoreNamedInsured</li><li>party.ProfitCenter</li></ul></pre>")

        //Edge from enum to target node
        edge = edges.values().find { Map edge1 -> 'partyrole.BasePartyRole.Parties' == edge1.fromName && 'party.ProfitCenter' == edge1.toName}
        assert 'party.ProfitCenter' == edge.fromFieldName
        assert '3' == edge.level
        assert !edge.label
        assert 'Valid value party.ProfitCenter cardinality 0:1' == edge.title

        //Target node under enum
        String nodeName = 'party.ProfitCenter'
        node = nodes.values().find { Map node1 -> nodeName == node1.label }
        node = loadNodeDetails(node)
        checkNode(nodeName, nodeName)
        assert nodeName == node.fromFieldName
        assert 'PARTY' == node.group
        assert '3' == node.level
        assert 'rpm.enum.partyrole.BasePartyRole.Parties' == node.sourceCubeName
        assert 'partyrole.BasePartyRole.Parties' == node.sourceDescription
        assert  [] == node.typesToAdd
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope
        assert (node.details as String).contains("${DETAILS_LABEL_FIELDS}<pre><ul><li>roleRefCode</li><li>fullName</li><li>fein</li></ul></pre>")
    }

    @Test
    void testLoadGraph_checkStructure()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                             coverage         : 'FCoverage',
                             risk             : 'WProductOps'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.class.Coverage'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        loadGraph(options)

        assert nodes.size() == 5
        assert edges.size() == 4

        assert nodes.values().find { Map node -> 'FCoverage' == node.label}
        assert nodes.values().find { Map node -> 'ICoverage' == node.label}
        assert nodes.values().find { Map node -> 'CCCoverage' == node.label}
        assert nodes.values().find { Map node -> "${UNABLE_TO_LOAD}Location".toString() == node.label}
        assert nodes.values().find { Map node -> "${VALID_VALUES_FOR_FIELD_SENTENCE_CASE}Coverages on FCoverage".toString() == node.title}

        assert edges.values().find { Map edge -> 'FCoverage' == edge.fromName && 'Coverage.Coverages' == edge.toName}
        assert edges.values().find { Map edge -> 'Coverage.Coverages' == edge.fromName && 'ICoverage' == edge.toName}
        assert edges.values().find { Map edge -> 'Coverage.Coverages' == edge.fromName && 'CCCoverage' == edge.toName}
        assert edges.values().find { Map edge -> 'CCCoverage' == edge.fromName && 'Location' == edge.toName}
    }

    @Test
    void testLoadGraph_checkStructure_nonEPM()
    {
        Map availableScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap

        //Load graph
        String startCubeName = 'rpm.class.partyrole.LossPrevention'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        loadGraph(options)

        assert nodes.size() == 4
        assert edges.size() == 3

        assert nodes.values().find { Map node ->'rpm.class.partyrole.LossPrevention' == node.cubeName}
        assert nodes.values().find { Map node ->'rpm.class.party.MoreNamedInsured' == node.cubeName}
        assert nodes.values().find { Map node ->'rpm.class.party.ProfitCenter' == node.cubeName}
        assert nodes.values().find { Map node ->'rpm.enum.partyrole.BasePartyRole.Parties' == node.cubeName}

        assert edges.values().find { Map edge ->'partyrole.BasePartyRole.Parties' == edge.fromName && 'party.ProfitCenter' == edge.toName}
        assert edges.values().find { Map edge ->'partyrole.BasePartyRole.Parties' == edge.fromName && 'party.MoreNamedInsured' == edge.toName}
        assert edges.values().find { Map edge ->'partyrole.LossPrevention' == edge.fromName && 'partyrole.BasePartyRole.Parties' == edge.toName}
    }

    @Test
    void testLoadGraph_checkNodeAndEdge()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                             coverage         : 'FCoverage'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        Map enumScope = new CaseInsensitiveMap(utilizedScope)
        enumScope.sourceFieldName = 'Coverages'

        Map availableEnumScope = new CaseInsensitiveMap(availableScope)
        availableEnumScope.sourceFieldName = 'Coverages'

        Map ccCoverageScope = new CaseInsensitiveMap(utilizedScope)
        ccCoverageScope.coverage = 'CCCoverage'

        Map availableCCCoverageScope = new CaseInsensitiveMap(availableScope)
        availableCCCoverageScope.putAll(ccCoverageScope)
        availableCCCoverageScope.sourceCoverage = 'FCoverage'
        availableCCCoverageScope.sourceFieldName = 'Coverages'

        //Load graph
        String startCubeName = 'rpm.class.Coverage'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options)

        //Top level source node
        checkNode('FCoverage', 'Coverage')
        assert 'rpm.class.Coverage' == node.cubeName
        assert null == node.fromFieldName
        assert 'COVERAGE' == node.group
        assert '1' == node.level
        assert null == node.sourceCubeName
        assert null == node.sourceDescription
        assert ['Coverage', 'Deductible', 'Limit', 'Premium', 'Rate', 'Ratefactor', 'Role'] == node.typesToAdd
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope
        assert (node.details as String).contains("${DETAILS_LABEL_FIELDS}<pre><ul><li>Coverages</li><li>Exposure</li><li>StatCode</li></ul></pre>")

        //Edge from top level node to enum
        Map edge = edges.values().find { Map edge -> 'FCoverage' == edge.fromName && 'Coverage.Coverages' == edge.toName}
        assert 'Coverages' == edge.fromFieldName
        assert '2' == edge.level
        assert 'Coverages' == edge.label
        assert "Field Coverages cardinality ${V_MIN_CARDINALITY}:${V_MAX_CARDINALITY}".toString() == edge.title

        //Enum node under top level node
        String nodeTitle = "${VALID_VALUES_FOR_FIELD_SENTENCE_CASE}Coverages on FCoverage"
        node = nodes.values().find {Map node1 ->  nodeTitle == node1.title}
        node = loadNodeDetails(node)
        checkEnumNode(nodeTitle, '', false)
        assert 'rpm.enum.Coverage.Coverages' == node.cubeName
        assert 'Coverages' == node.fromFieldName
        assert 'COVERAGE_ENUM' == node.group
        assert '2' == node.level
        assert 'rpm.class.Coverage' == node.sourceCubeName
        assert 'FCoverage' == node.sourceDescription
        assert enumScope == node.scope
        assert availableEnumScope == node.availableScope
        assert null == node.typesToAdd
        assert (node.details as String).contains("${DETAILS_LABEL_FIELDS}<pre><ul><li>CCCoverage</li><li>ICoverage</li></ul></pre>")

        //Edge from enum to target node
        edge = edges.values().find { Map edge1 -> 'Coverage.Coverages' == edge1.fromName && 'CCCoverage' == edge1.toName}
        assert 'CCCoverage' == edge.fromFieldName
        assert '3' == edge.level
        assert !edge.label
        assert "Valid value CCCoverage cardinality ${V_MIN_CARDINALITY}:${V_MAX_CARDINALITY}".toString() == edge.title

        //Target node of top level node
        String nodeName = 'CCCoverage'
        node = nodes.values().find { Map node1 -> nodeName == node1.label }
        node = loadNodeDetails(node)
        checkNode(nodeName, 'Coverage')
        assert 'rpm.class.Coverage' == node.cubeName
        assert nodeName == node.fromFieldName
        assert 'COVERAGE' == node.group
        assert '3' == node.level
        assert 'rpm.enum.Coverage.Coverages' == node.sourceCubeName
        assert 'field Coverages on FCoverage' == node.sourceDescription
        assert ['Coverage', 'Deductible', 'Limit', 'Premium', 'Rate', 'Ratefactor', 'Role'] == node.typesToAdd
        assert ccCoverageScope == node.scope
        assert availableCCCoverageScope == node.availableScope
        assert (node.details as String).contains("${DETAILS_LABEL_FIELDS}<pre><ul><li>Exposure</li><li>Location</li><li>StatCode</li><li>field1</li><li>field2</li><li>field3</li><li>field4</li></ul></pre>")
    }

    @Test
    void testGetCellValues_classNode_show()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                             coverage         : 'CCCoverage'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.class.Coverage'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options)
        checkNode('CCCoverage', 'Coverage')
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope

        //Simulate that the user clicks Show Traits for the node. Optional scope prompts display.
        node.showCellValues = true
        node.showingHidingCellValues = true
        node = loadNodeDetails(node)
        checkNode('CCCoverage', 'Coverage', '', DEFAULTS_WERE_USED, false, true)
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope
        String nodeDetails = node.details as String
        assert nodeDetails.contains("Exposure</b></li><pre><ul><li>r:declared: true</li><li>r:exists: true</li><li>r:extends: DataElementInventory</li><li>r:rpmType: string</li></ul></pre><li><b>")
        assert nodeDetails.contains("Location</b></li><pre><ul><li>r:declared: true</li><li>r:exists: true</li><li>r:rpmType: Risk</li><li>v:max: 1</li><li>v:min: 0</li></ul></pre><li><b>")
        assert nodeDetails.contains("StatCode</b></li><pre><ul><li>r:declared: true</li><li>r:defaultValue: None</li><li>r:exists: true</li><li>r:extends: DataElementInventory[StatCode]</li><li>r:rpmType: string</li></ul></pre>")
        assert nodeDetails.contains("field1</b></li><pre><ul><li>r:declared: true</li><li>r:defaultValue: DEI default for field1</li><li>r:exists: true</li><li>r:extends: DataElementInventory</li><li>r:rpmType: string</li></ul></pre><li><b>")
        assert nodeDetails.contains("field2</b></li><pre><ul><li>r:declared: true</li><li>r:defaultValue: DEI default for field2</li><li>r:exists: true</li><li>r:extends: DataElementInventory</li><li>r:rpmType: string</li></ul></pre><li><b>")
        assert nodeDetails.contains("field3</b></li><pre><ul><li>r:declared: true</li><li>r:defaultValue: DEI default for field3</li><li>r:exists: true</li><li>r:extends: DataElementInventory</li><li>r:rpmType: string</li></ul></pre><li><b>")
        assert nodeDetails.contains("field4</b></li><pre><ul><li>r:declared: true</li><li>r:defaultValue: DEI default for field4</li><li>r:exists: true</li><li>r:extends: DataElementInventory</li><li>r:rpmType: string</li></ul></pre></ul></pre>")
        assert nodeDetails.contains("${DETAILS_LABEL_CLASS_TRAITS}</b><pre><ul><li>r:exists: true</li><li>r:name: CCCoverage</li><li>r:scopedName: CCCoverage</li></ul></pre><br><b>")
    }

    @Test
    void testGetCellValues_classNode_show_unboundAxes_changeToNonDefault()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                             coverage         : 'CCCoverage'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.class.Coverage'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options)
        checkNode('CCCoverage', 'Coverage')

        //Simulate that the user clicks Show Traits for the node
        node.showCellValues = true
        node.showingHidingCellValues = true
        node = loadNodeDetails(node)
        checkNode('CCCoverage', 'Coverage', '', DEFAULTS_WERE_USED, false, true)
        checkScopePromptTitle(node, 'businessDivisionCode', false, '  - rpm.scope.class.Coverage.traits.StatCode\n  - rpm.scope.class.Coverage.traits.field1And2\n  - rpm.scope.class.Coverage.traits.field4')
        checkScopePromptDropdown(node, 'businessDivisionCode', 'Default', ['AAADIV', 'BBBDIV', DEFAULT], [], true)
        checkScopePromptTitle(node, 'program', false, '  - rpm.scope.class.Coverage.traits.field1And2\n  - rpm.scope.class.Coverage.traits.field4')
        checkScopePromptDropdown(node, 'program', 'Default', ['program1', 'program2', 'program3', DEFAULT], [], true)
        checkScopePromptTitle(node, 'type', false, '  - rpm.scope.class.Coverage.traits.field1And2\n  - rpm.scope.class.Coverage.traits.field3CovC\n  - rpm.scope.class.Coverage.traits.field4')
        checkScopePromptDropdown(node, 'type', 'Default', ['type1', 'type2', 'type3', 'typeA', 'typeB', DEFAULT], [], true)
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope

        //Simulate that the user picks businessDivisionCode = AAADIV
        utilizedScope.businessDivisionCode = 'AAADIV'
        availableScope.businessDivisionCode = 'AAADIV'
        node.availableScope = new CaseInsensitiveMap(availableScope)
        node.showingHidingCellValues = false
        node = loadScopeChange(node)
        checkNode('CCCoverage', 'Coverage', '', DEFAULTS_WERE_USED, false, true)
        checkScopePromptTitle(node, 'businessDivisionCode', false, '  - rpm.scope.class.Coverage.traits.StatCode\n  - rpm.scope.class.Coverage.traits.field1And2\n  - rpm.scope.class.Coverage.traits.field4')
        checkScopePromptDropdown(node, 'businessDivisionCode', 'AAADIV', ['AAADIV', 'BBBDIV', DEFAULT], [], true)
        checkScopePromptTitle(node, 'program', false, '  - rpm.scope.class.Coverage.traits.field1And2\n  - rpm.scope.class.Coverage.traits.field4')
        checkScopePromptDropdown(node, 'program', 'Default', ['program1', 'program2', 'program3', DEFAULT], [], true)
        checkScopePromptTitle(node, 'type', false, '  - rpm.scope.class.Coverage.traits.field1And2\n  - rpm.scope.class.Coverage.traits.field3CovC\n  - rpm.scope.class.Coverage.traits.field4')
        checkScopePromptDropdown(node, 'type', 'Default', ['type1', 'type2', 'type3', 'typeA', 'typeB', DEFAULT], [], true)
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope

        String nodeDetails = node.details as String
        assert nodeDetails.contains("Exposure</b></li><pre><ul><li>r:declared: true</li><li>r:exists: true</li><li>r:extends: DataElementInventory</li><li>r:rpmType: string</li></ul></pre><li><b>")
        assert nodeDetails.contains("Location</b></li><pre><ul><li>r:declared: true</li><li>r:exists: true</li><li>r:rpmType: Risk</li><li>v:max: 1</li><li>v:min: 0</li></ul></pre><li><b>")
        assert nodeDetails.contains("StatCode</b></li><pre><ul><li>r:declared: true</li><li>r:defaultValue: 1133</li><li>r:exists: true</li><li>r:extends: DataElementInventory[StatCode]</li><li>r:rpmType: string</li></ul></pre>")
        assert nodeDetails.contains("field1</b></li><pre><ul><li>r:declared: true</li><li>r:defaultValue: 1133</li><li>r:exists: true</li><li>r:extends: DataElementInventory</li><li>r:rpmType: string</li></ul></pre><li><b>")
        assert nodeDetails.contains("field2</b></li><pre><ul><li>r:declared: true</li><li>r:defaultValue: 1133</li><li>r:exists: true</li><li>r:extends: DataElementInventory</li><li>r:rpmType: string</li></ul></pre><li><b>")
        assert nodeDetails.contains("field3</b></li><pre><ul><li>r:declared: true</li><li>r:defaultValue: DEI default for field3</li><li>r:exists: true</li><li>r:extends: DataElementInventory</li><li>r:rpmType: string</li></ul></pre><li><b>")
        assert nodeDetails.contains("field4</b></li><pre><ul><li>r:declared: true</li><li>r:defaultValue: 1133</li><li>r:exists: true</li><li>r:extends: DataElementInventory</li><li>r:rpmType: string</li></ul></pre></ul></pre>")
        assert nodeDetails.contains("${DETAILS_LABEL_CLASS_TRAITS}</b><pre><ul><li>r:exists: true</li><li>r:name: CCCoverage</li><li>r:scopedName: CCCoverage</li></ul></pre><br><b>")
    }

    @Test
    void testGetCellValues_classNode_show_URLs()
    {
        String httpsURL = 'https://mail.google.com'
        String fileURL = 'file:///C:/Users/bheekin/Desktop/honey%20badger%20thumbs%20up.jpg'
        String httpURL = 'http://www.google.com'
        String fileLink = """<a href="#" onclick='window.open("${fileURL}");return false;'>${fileURL}</a>"""
        String httpsLink = """<a href="#" onclick='window.open("${httpsURL}");return false;'>${httpsURL}</a>"""
        String httpLink = """<a href="#" onclick='window.open("${httpURL}");return false;'>${httpURL}</a>"""

        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                             coverage         : 'AdmCoverage'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.class.Coverage'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options)
        checkNode('AdmCoverage', 'Coverage')
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope

        //Simulate that the user clicks Show Traits for the node.
        //An optional scope prompt for business division code shows.
        node.showCellValues = true
        node.showingHidingCellValues = true
        node = loadNodeDetails(node)
        checkNode('AdmCoverage', 'Coverage', '', DEFAULTS_WERE_USED, false, true)
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope

        //Simulate that the user picks businessDivisionCode = AAADIV
        utilizedScope.businessDivisionCode = 'AAADIV'
        availableScope.businessDivisionCode = 'AAADIV'
        node.availableScope = new CaseInsensitiveMap(availableScope)
        node.showingHidingCellValues = false
        node = loadScopeChange(node)
        checkNode('AdmCoverage', 'Coverage', '', '', false, true)
        String nodeDetails = node.details as String
        checkScopePromptTitle(node, 'businessDivisionCode', false, 'rpm.scope.class.Coverage.traits.StatCode')
        checkScopePromptDropdown(node, 'businessDivisionCode', 'AAADIV', ['AAADIV', 'BBBDIV', DEFAULT], [], true)
        assert nodeDetails.contains("Exposure</b></li><pre><ul><li>r:declared: true</li><li>r:defaultValue: ${fileLink}</li><li>r:exists: true</li><li>r:extends: DataElementInventory</li><li>r:rpmType: string</li></ul></pre>")
        assert nodeDetails.contains("Location</b></li><pre><ul><li>r:declared: true</li><li>r:defaultValue: ${httpLink}</li><li>r:exists: true</li><li>r:rpmType: Risk</li><li>v:max: 1</li><li>v:min: 0</li></ul></pre><li><b>")
        assert nodeDetails.contains("StatCode</b></li><pre><ul><li>r:declared: true</li><li>r:defaultValue: ${httpsLink}</li><li>r:exists: true</li><li>r:extends: DataElementInventory[StatCode]</li><li>r:rpmType: string</li></ul></pre></ul></pre>")
        assert nodeDetails.contains("${DETAILS_LABEL_CLASS_TRAITS}</b><pre><ul><li>r:exists: true</li><li>r:name: AdmCoverage</li><li>r:scopedName: AdmCoverage</li></ul></pre><br><b>")
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope
    }

    @Test
    void testGetCellValues_enumNode_show()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                             coverage         : 'FCoverage'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.class.Coverage'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        loadGraph(options)
        String enumNodeTitle = "${VALID_VALUES_FOR_FIELD_SENTENCE_CASE}Coverages on FCoverage"
        Map enumNode = nodes.values().find {Map node1 ->  enumNodeTitle == node1.title}
        enumNode = loadNodeDetails(enumNode)
        checkEnumNode(enumNodeTitle)

        //Simulate that the user clicks Show Traits for the node
        enumNode.showCellValues = true
        enumNode.showingHidingCellValues = true
        enumNode = loadNodeDetails(enumNode)
        checkEnumNode(enumNodeTitle, '', false, true)
        utilizedScope.sourceFieldName = 'Coverages'
        assert utilizedScope == enumNode.scope
        availableScope.sourceFieldName = 'Coverages'
        assert availableScope == enumNode.availableScope
        String nodeDetails = enumNode.details as String
        assert nodeDetails.contains("${DETAILS_LABEL_FIELDS_AND_TRAITS}</b><pre><ul><li><b>CCCoverage</b></li><pre><ul><li>r:declared: true</li><li>r:exists: true</li><li>r:name: CCCoverage</li><li>v:max: 999999</li><li>v:min: 0</li></ul></pre><li><b>ICoverage</b></li><pre><ul><li>r:declared: true</li><li>r:exists: true</li><li>r:name: ICoverage</li><li>v:max: 1</li><li>v:min: 0</li></ul>")
        assert nodeDetails.contains("${DETAILS_LABEL_CLASS_TRAITS}</b><pre><ul><li>r:exists: true</li></ul></pre><br><b>")
    }

    @Test
    void testGetCellValues_classNode_hide()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                             coverage         : 'TCoverage'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.class.Coverage'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options)
        checkNode('TCoverage', 'Coverage')
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope

        //Simulate that the user clicks Show Traits for the node.
        //Required node scope prompt shows for points. No traits show yet.
        node.showCellValues = true
        node.showingHidingCellValues = true
        node = loadNodeDetails(node)
        checkNode('TCoverage', 'Coverage', '', ADDITIONAL_SCOPE_REQUIRED, true, true)
        checkScopePromptTitle(node, 'points', true, 'rpm.scope.class.Coverage.traits.fieldTCoverage')
        checkScopePromptDropdown(node, 'points', '', ['A', 'B', 'C'], [DEFAULT], true)
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope

        //Simulate that the user picks points = A in the node scope prompt.
        //Required node scope prompt shows for points. Traits now show
        utilizedScope.points = 'A'
        availableScope.points = 'A'
        node.showCellValues = true
        node.showingHidingCellValues = false
        node.availableScope = new CaseInsensitiveMap(availableScope)
        node = loadScopeChange(node)
        checkNode('TCoverage', 'Coverage', '', DEFAULTS_WERE_USED, false, true)
        checkScopePromptTitle(node, 'points', true, 'rpm.scope.class.Coverage.traits.fieldTCoverage')
        checkScopePromptDropdown(node, 'points', 'A', ['A', 'B', 'C'], [DEFAULT], true)
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope

        //Simulate that the user clicks Hide Traits for the node.
        //Required node scope prompt shows for points. No traits show.
        node.showCellValues = false
        node.showingHidingCellValues = true
        node = loadNodeDetails(node)
        checkNode('TCoverage', 'Coverage')
        checkScopePromptTitle(node, 'points', true, 'rpm.scope.class.Coverage.traits.fieldTCoverage')
        checkScopePromptDropdown(node, 'points', 'A', ['A', 'B', 'C'], [DEFAULT], true)
        utilizedScope.remove('points')
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope
      }

    @Test
    void testLoadGraph_cubeNotFound()
    {
        NCube cube = NCubeManager.getCube(appId, 'rpm.enum.partyrole.BasePartyRole.Parties')
        try
        {
            //Change enum to have reference to non-existing cube
            cube.addColumn((AXIS_NAME), 'party.NoCubeExists')
            cube.setCell(true,[name:'party.NoCubeExists', trait: R_EXISTS])

            Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap
            Map availableScope = defaultRpmScope + utilizedScope

            //Load graph
            String startCubeName = 'rpm.class.partyrole.LossPrevention'
            Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
            loadGraph(options, true)

            assert 1 == messages.size()
            assert 'No cube exists with name of rpm.class.party.NoCubeExists. Cube not included in the visualization.' == messages.first()
        }
        finally
        {
            //Reset cube
            cube.deleteColumn((AXIS_NAME), 'party.NoCubeExists')
            assert !cube.findColumn(AXIS_NAME, 'party.NoCubeExists')
        }
    }

    @Test
    void testLoadGraph_effectiveVersionApplied_beforeFieldAddAndObsolete()
    {
        Map utilizedScope = [_effectiveVersion: '1.0.0',
                             product          : 'WProduct'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.class.Product'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options)
        String nodeDetails = node.details as String
        assert nodeDetails.contains("${DETAILS_LABEL_FIELDS}<pre><ul><li>CurrentCommission</li><li>CurrentExposure</li><li>Risks</li><li>fieldObsolete101</li></ul></pre>")
    }

    @Test
    void testLoadGraph_effectiveVersionApplied_beforeFieldAddAfterFieldObsolete()
    {
        Map utilizedScope = [_effectiveVersion: '1.0.1',
                             product          : 'WProduct'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.class.Product'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options)
        String nodeDetails = node.details as String
        assert nodeDetails.contains("${DETAILS_LABEL_FIELDS}<pre><ul><li>CurrentCommission</li><li>CurrentExposure</li><li>Risks</li></ul></pre>")
    }

    @Test
    void testLoadGraph_effectiveVersionApplied_afterFieldAddAndObsolete()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                             product          : 'WProduct'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.class.Product'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options)
        String nodeDetails = node.details as String
        assert nodeDetails.contains("${DETAILS_LABEL_FIELDS}<pre><ul><li>CurrentCommission</li><li>CurrentExposure</li><li>Risks</li><li>fieldAdded102</li></ul></pre>")
    }

    @Test
    void testLoadGraph_validRpmClass()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.class.ValidRpmClass'
        createNCubeWithValidRpmClass(startCubeName)
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options)

        checkValidRpmClass( startCubeName)
    }

    @Test
    void testLoadGraph_validRpmClass_notStartWithRpmClass()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.klutz.ValidRpmClass'
        createNCubeWithValidRpmClass(startCubeName)
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options, true)

        assert 1 == messages.size()
        String message = messages.first()
        assert "Starting cube for visualization must begin with 'rpm.class', ${startCubeName} does not.".toString() == message
    }

    @Test
    void testLoadGraph_validRpmClass_startCubeNotFound()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'ValidRpmClass'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options, true)

        assert 1 == messages.size()
        String message = messages.first()
        assert "No cube exists with name of ${startCubeName} for application id ${appId.toString()}".toString() == message
    }

    @Test
    void testLoadGraph_validRpmClass_noTraitAxis()
    {
        String startCubeName = 'rpm.class.ValidRpmClass'
        createNCubeWithValidRpmClass(startCubeName)
        NCube cube = NCubeManager.getCube(appId, startCubeName)
        cube.deleteAxis(AXIS_TRAIT)

        Map scope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap
        topNodeScope = scope
        Map options = [startCubeName: startCubeName, scope: topNodeScope]

        Map node = loadGraph(options, true)
        assert 1 == messages.size()
        String message = messages.first()
        assert "Cube ${startCubeName} is not a valid rpm class since it does not have both a field axis and a traits axis.".toString() == message
    }

    @Test
    void testLoadGraph_validRpmClass_noFieldAxis()
    {
        String startCubeName = 'rpm.class.ValidRpmClass'
        createNCubeWithValidRpmClass(startCubeName)
        NCube cube = NCubeManager.getCube(appId, startCubeName)
        cube.deleteAxis(AXIS_FIELD)

        Map scope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap
        topNodeScope = scope
        Map options = [startCubeName: startCubeName, scope: topNodeScope]

        Map node = loadGraph(options, true)
        assert 1 == messages.size()
        String message = messages.first()
        assert "Cube ${startCubeName} is not a valid rpm class since it does not have both a field axis and a traits axis.".toString() == message
    }

    @Test
    void testLoadGraph_validRpmClass_noCLASSTRAITSField()
    {
        String startCubeName = 'rpm.class.ValidRpmClass'
        createNCubeWithValidRpmClass(startCubeName)
        NCube cube = NCubeManager.getCube(appId, startCubeName)
        cube.getAxis(AXIS_FIELD).columns.remove(CLASS_TRAITS)

        Map scope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap
        topNodeScope = scope
        Map options = [startCubeName: startCubeName, scope: topNodeScope]

        Map node = loadGraph(options)
        checkValidRpmClass( startCubeName)
    }

    @Test
    void testLoadGraph_validRpmClass_noRExistsTrait()
    {
        String startCubeName = 'rpm.class.ValidRpmClass'
        createNCubeWithValidRpmClass(startCubeName)
        NCube cube = NCubeManager.getCube(appId, startCubeName)
        cube.getAxis(AXIS_TRAIT).columns.remove(R_EXISTS)

        Map scope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap
        topNodeScope = scope
        Map options = [startCubeName: startCubeName, scope: topNodeScope]

        Map node = loadGraph(options)
        checkValidRpmClass(startCubeName)
    }

    @Test
    void testLoadGraph_validRpmClass_noRRpmTypeTrait()
    {
        String startCubeName = 'rpm.class.ValidRpmClass'
        createNCubeWithValidRpmClass(startCubeName)
        NCube cube = NCubeManager.getCube(appId, startCubeName)
        cube.getAxis(AXIS_TRAIT).columns.remove(R_RPM_TYPE)

        Map scope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap
        topNodeScope = scope
        Map options = [startCubeName: startCubeName, scope: topNodeScope]

        Map node = loadGraph(options)
        checkValidRpmClass( startCubeName)
    }

    @Test
    void testLoadGraph_invokedWithParentVisualizerInfoClass()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                             product          : 'WProduct',
                             coverage         : 'FCoverage',
                             risk             : 'WProductOps'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope


        VisualizerInfo notRpmVisInfo = new VisualizerInfo()
        notRpmVisInfo.groupSuffix = 'shouldGetReset'

        //Load graph
        String startCubeName = 'rpm.class.Coverage'
        Map options = [startCubeName: startCubeName, visInfo: notRpmVisInfo, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options)

        assert 'RpmVisualizerInfo' == visInfo.class.simpleName
        assert '_ENUM' ==  visInfo.groupSuffix
        assert 'COVERAGE' == node.group
    }

    @Test
    void testLoadGraph_exceptionInMinimumTrait()
    {
        NCube cube = NCubeManager.getCube(appId, 'rpm.scope.class.Coverage.traits')
        Map coordinate = [(AXIS_FIELD): 'Exposure', (AXIS_TRAIT): R_EXISTS, coverage: 'FCoverage'] as Map

        try
        {
            //Change r:exists trait for FCoverage to throw an exception
            String expression = 'int a = 5; int b = 0; return a / b'
            cube.setCell(new GroovyExpression(expression, null, false), coordinate)

            Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                                 product          : 'WProduct'] as CaseInsensitiveMap
            Map availableScope = defaultRpmScope + utilizedScope

            //Load graph
            String startCubeName = 'rpm.class.Product'
            Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
            loadGraph(options)

            Map node = nodes.values().find { Map node1 -> "${UNABLE_TO_LOAD}FCoverage".toString() == node1.label }
            node = loadNodeDetails(node)
            checkNode('FCoverage', 'Coverage', UNABLE_TO_LOAD, 'Unable to load the class due to an exception.', true)
            String nodeDetails = node.details as String
            assert nodeDetails.contains(DETAILS_LABEL_MESSAGE)
            assert nodeDetails.contains(DETAILS_LABEL_ROOT_CAUSE)
            assert nodeDetails.contains('java.lang.ArithmeticException: Division by zero')
            assert nodeDetails.contains(DETAILS_LABEL_STACK_TRACE)
        }
        finally
        {
            //Reset cube
            cube.setCell(new GroovyExpression('true', null, false), coordinate)
        }
    }

    @Test
    void testLoadGraph_scopePrompt_graph_initial()
    {
        //Load graph with no scope
        String startCubeName = 'rpm.class.Product'
        topNodeScope = new CaseInsensitiveMap()
        Map options = [startCubeName: startCubeName, scope: topNodeScope]
        Map node = loadGraph(options)
        assert 1 == nodes.size()
        assert 0 == edges.size()

        //Check graph scope prompt
        Map expectedAvailableScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
        Map topNode = nodes[1] as Map
        assert (topNode.scopeMessage as String).contains('Reset scope')
        checkTopNodeScope()
        checkOptionalGraphScope()
    }

    @Test
    void testLoadGraph_scopePrompt_nodes_initial()
    {
        //Load graph with no scope
        String startCubeName = 'rpm.class.Product'
        topNodeScope = new CaseInsensitiveMap()
        Map options = [startCubeName: startCubeName, scope: topNodeScope]
        Map node = loadGraph(options)
        assert 1 == nodes.size()
        assert 0 == edges.size()

        //Check starting node scope prompt
        checkNode('Product', 'Product', ADDITIONAL_SCOPE_REQUIRED_FOR, ADDITIONAL_SCOPE_REQUIRED, true)
        String nodeDetails = node.details as String
        checkScopePromptTitle(node, 'product', true, 'rpm.scope.class.Product.traits')
        checkScopePromptDropdown(node, 'product', '', ['AProduct', 'BProduct', 'GProduct', 'UProduct', 'WProduct'], [DEFAULT])
        assert node.availableScope == [_effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
        assert node.scope == new CaseInsensitiveMap()
    }

    @Test
    void testLoadGraph_scopePrompt_graph_afterProductSelected()
    {
        //Load graph with no scope
        String startCubeName = 'rpm.class.Product'
        topNodeScope = new CaseInsensitiveMap()
        Map options = [startCubeName: startCubeName, scope: topNodeScope]
        Map node = loadGraph(options)

        //User picks AProduct. Reload.
        topNodeScope.product = 'AProduct'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)
        assert 8 == nodes.size()
        assert 7 == edges.size()

        //Check graph scope prompt
        Map expectedAvailableScope = [product: 'AProduct',_effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
        assert scopeInfo.scope == expectedAvailableScope
        //TODO: assert scopeInfo.scopeMessage.contains('Reset scope')
        checkTopNodeScope('AProduct')
        checkOptionalGraphScope('AProduct')
    }

    @Test
    void testLoadGraph_scopePrompt_nodes_afterProductSelected()
    {
        //Load graph with no scope
        String startCubeName = 'rpm.class.Product'
        topNodeScope = new CaseInsensitiveMap()
        Map options = [startCubeName: startCubeName, scope: topNodeScope]
        Map node = loadGraph(options)

        //User picks AProduct. Reload.
        topNodeScope.product = 'AProduct'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)
        assert 8 == nodes.size()
        assert 7 == edges.size()

        //AProduct has no scope prompt
        checkNode('AProduct', 'Product')
        checkNoScopePrompt(node)
        assert node.availableScope == [product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
        assert node.scope == [product: 'AProduct',_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap

        //Product.Risks enum has one default scope prompt, no required prompts
        checkEnumNode("${VALID_VALUES_FOR_FIELD_SENTENCE_CASE}Risks on AProduct", '', false)
        String nodeDetails = node.details as String
        checkScopePromptTitle(node, 'div', false, 'rpm.scope.enum.Product.Risks.traits.exists')
        checkScopePromptDropdown(node, 'div', DEFAULT, ['div1', 'div2', DEFAULT], ['div3'])
        checkNoScopePrompt(node, 'state')
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'pgm')
        assert node.availableScope == [sourceFieldName: 'Risks', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
        assert node.scope == [sourceFieldName: 'Risks', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap

        //ARisk has two default scope prompts, no required prompts
        checkNode('ARisk', 'Risk', '', DEFAULTS_WERE_USED, false)
        nodeDetails = node.details as String
        checkScopePromptTitle(node, 'div', false, 'rpm.scope.class.Risk.traits.fieldARisk')
        checkScopePromptDropdown(node, 'div', DEFAULT, ['div1', DEFAULT], ['div2', 'div3'])
        checkScopePromptTitle(node, 'state', false, 'rpm.scope.class.Risk.traits.fieldARisk')
        checkScopePromptDropdown(node, 'state', DEFAULT, ['KY', 'NY', 'OH', DEFAULT], ['IN', 'GA'])
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'pgm')
        assert node.availableScope == [sourceFieldName: 'Risks', risk: 'ARisk', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
        assert node.scope == [risk: 'ARisk', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap

        //BRisk has one required scope prompt, no default prompts
        checkNode('BRisk', 'Risk', ADDITIONAL_SCOPE_REQUIRED_FOR, ADDITIONAL_SCOPE_REQUIRED, true)
        nodeDetails = node.details as String
        checkScopePromptTitle(node, 'pgm', true, 'rpm.scope.class.Risk.traits.fieldBRisk')
        checkScopePromptDropdown(node, 'pgm', '', ['pgm3'], ['pgm1', 'pgm2', DEFAULT])
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'div')
        checkNoScopePrompt(node, 'state')
        assert node.availableScope == [sourceFieldName: 'Risks', risk: 'BRisk', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
        assert node.scope == new CaseInsensitiveMap()

        //ACoverage has two required scope prompts, no default prompts
        checkNode('ACoverage', 'Coverage', ADDITIONAL_SCOPE_REQUIRED_FOR, ADDITIONAL_SCOPE_REQUIRED, true)
        nodeDetails = node.details as String
        checkScopePromptTitle(node, 'div', true, 'rpm.scope.class.Coverage.traits.fieldACoverage')
        checkScopePromptDropdown(node, 'div', '', ['div1', 'div2'], [DEFAULT, 'div3'])
        checkScopePromptTitle(node, 'pgm', true, 'rpm.scope.class.Coverage.traits.fieldACoverage')
        checkScopePromptDropdown(node, 'pgm', '', ['pgm1', 'pgm2', 'pgm3'], [DEFAULT])
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'state')
        assert node.availableScope == [coverage: 'ACoverage', sourceFieldName: 'Coverages', risk: 'ARisk', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
        assert node.scope == new CaseInsensitiveMap()

        //BCoverage has one required scope prompt, one default scope prompt. The default scope prompt doesn't show yet since
        //there is currently a required scope prompt for the node.
        checkNode('BCoverage', 'Coverage', ADDITIONAL_SCOPE_REQUIRED_FOR, ADDITIONAL_SCOPE_REQUIRED, true)
        nodeDetails = node.details as String
        checkScopePromptTitle(node, 'div', true, 'rpm.scope.class.Coverage.traits.fieldBCoverage')
        checkScopePromptDropdown(node, 'div', '', ['div3'], ['div1', 'div2', DEFAULT])
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'pgm')
        checkNoScopePrompt(node, 'state')
        assert node.availableScope == [coverage: 'BCoverage', sourceFieldName: 'Coverages', risk: 'ARisk', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
        assert node.scope == new CaseInsensitiveMap()

        //CCoverage has one default scope prompt, no required prompts
        checkNode('CCoverage', 'Coverage', '', DEFAULTS_WERE_USED, false)
        nodeDetails = node.details as String
        checkScopePromptTitle(node, 'state', false, 'rpm.scope.class.Coverage.traits.fieldCCoverage')
        checkScopePromptDropdown(node, 'state', 'Default', ['GA', 'IN', 'NY', DEFAULT], ['KY', 'OH'])
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'div')
        checkNoScopePrompt(node, 'pgm')
        assert node.availableScope == [sourceFieldName: 'Coverages', coverage: 'CCoverage', risk: 'ARisk', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
        assert node.scope == [coverage: 'CCoverage', risk: 'ARisk', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
    }

    @Test
    void testLoadGraph_scopePrompt_graph_afterInvalidProductEntered()
    {
        //Load graph with no scope
        String startCubeName = 'rpm.class.Product'
        topNodeScope = new CaseInsensitiveMap()
        Map options = [startCubeName: startCubeName, scope: topNodeScope]
        Map node = loadGraph(options)

        //User enters invalid XXXProduct. Reload.
        topNodeScope.product = 'XXXProduct'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)
        assert 1 == nodes.size()
        assert 0 == edges.size()

        //Check graph scope prompt
        checkTopNodeScope('XXXProduct')
        checkOptionalGraphScope()
    }

    @Test
    void testLoadGraph_scopePrompt_nodes_afterInvalidProductEntered()
    {
        //Load graph with no scope
        String startCubeName = 'rpm.class.Product'
        topNodeScope = new CaseInsensitiveMap()
        Map options = [startCubeName: startCubeName, scope: topNodeScope]
        Map node = loadGraph(options)

        //User enters invalid XXXProduct. Reload.
        topNodeScope.product = 'XXXProduct'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)
        assert 1 == nodes.size()
        assert 0 == edges.size()

        //Check starting node scope prompt
        checkNode('XXXProduct', 'Product', REQUIRED_SCOPE_VALUE_NOT_FOUND_FOR, IS_NOT_VALID_FOR, true)
        String nodeDetails = node.details as String
        checkScopePromptTitle(node, 'product', true, 'rpm.scope.class.Product.traits')
        checkScopePromptDropdown(node, 'product', 'XXXProduct', ['AProduct', 'BProduct', 'GProduct', 'UProduct', 'WProduct'], [DEFAULT])
        checkNoScopePrompt(node, 'div')
        checkNoScopePrompt(node, 'pgm')
        checkNoScopePrompt(node, 'state')
        assert node.availableScope == [product: 'XXXProduct',_effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
        assert node.scope == new CaseInsensitiveMap()
    }

    @Test
    void testLoadGraph_scopePrompt_graph_afterProductSelected_afterOptionalGraphScopeSelected()
    {
        //Load graph with no scope
        String startCubeName = 'rpm.class.Product'
        topNodeScope = new CaseInsensitiveMap()
        Map options = [startCubeName: startCubeName, scope: topNodeScope]
        Map node = loadGraph(options)
        assert 1 == nodes.size()
        assert 0 == edges.size()

        //User picks AProduct. Reload.
        topNodeScope = new CaseInsensitiveMap(scopeInfo.scope as Map)
        topNodeScope.product = 'AProduct'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)
        assert 8 == nodes.size()
        assert 7 == edges.size()

        //User picks pgm = pgm1, state = OH and div = div1. Reload after each.
        topNodeScope = new CaseInsensitiveMap(scopeInfo.scope as Map)
        topNodeScope.pgm = 'pgm1'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)

        topNodeScope = new CaseInsensitiveMap(scopeInfo.scope as Map)
        topNodeScope.state = 'OH'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)

        topNodeScope = new CaseInsensitiveMap(scopeInfo.scope as Map)
        topNodeScope.div = 'div1'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)

        assert 8 == nodes.size()
        assert 7 == edges.size()

        //Check graph scope prompt
        Map expectedAvailableScope = [pgm: 'pgm1', state: 'OH', div: 'div1', product: 'AProduct',_effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
        assert scopeInfo.scope == expectedAvailableScope
        //TODO:  assert scopeInfo.scopeMessage.contains('Reset scope')
        checkTopNodeScope('AProduct')
        checkOptionalGraphScope('AProduct', 'pgm1', 'OH', 'div1')

        //User changes to div = div3. Reload.
        topNodeScope = new CaseInsensitiveMap(scopeInfo.scope as Map)
        topNodeScope.div = 'div3'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)
        assert 8 == nodes.size()
        assert 7 == edges.size()

        //Check graph scope prompt - BCoverage no longer has missing required scope since div=div3, and as a result exposes a
        //new optional scope value for state (NM).
        expectedAvailableScope = [pgm: 'pgm1', state: 'OH', div: 'div3', product: 'AProduct',_effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
        assert scopeInfo.scope == expectedAvailableScope
        //TODO: assert scopeInfo.scopeMessage.contains('Reset scope')
        checkTopNodeScope('AProduct')
        checkOptionalGraphScope('AProduct', 'pgm1', 'OH', 'div3', true)
    }


    @Test
    void testLoadGraph_scopePrompt_nodes_afterProductSelected_afterOptionalGraphScopeSelected_once()
    {
        //Load graph with no scope
        String startCubeName = 'rpm.class.Product'
        topNodeScope = new CaseInsensitiveMap()
        Map options = [startCubeName: startCubeName, scope: topNodeScope]
        Map node = loadGraph(options)

        //User picks AProduct. Reload.
        topNodeScope.product = 'AProduct'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)
        assert 8 == nodes.size()
        assert 7 == edges.size()

        //User picks pgm = pgm1, state = OH and div = div1. Reload after each.
        topNodeScope = new CaseInsensitiveMap(scopeInfo.scope as Map)
        topNodeScope.pgm = 'pgm1'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)

        topNodeScope = new CaseInsensitiveMap(scopeInfo.scope as Map)
        topNodeScope.state = 'OH'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)

        topNodeScope = new CaseInsensitiveMap(scopeInfo.scope as Map)
        topNodeScope.div = 'div1'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)

        //AProduct has no scope prompt
        checkNode('AProduct', 'Product')
        checkNoScopePrompt(node)
        assert node.scope == [product: 'AProduct',_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap

        //Product.Risks enum has no scope prompt
        checkEnumNode("${VALID_VALUES_FOR_FIELD_SENTENCE_CASE}Risks on AProduct", '', false)
        checkNoScopePrompt(node)
        assert node.scope == [div: 'div1', sourceFieldName: 'Risks', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap

        //ARisk has no scope prompts
        checkNode('ARisk', 'Risk')
        checkNoScopePrompt(node)
        assert node.scope == [div: 'div1', state: 'OH', risk: 'ARisk', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap

        //BRisk has required scope prompt since requires pgm=pgm3
        checkNode('BRisk', 'Risk', REQUIRED_SCOPE_VALUE_NOT_FOUND_FOR, IS_NOT_VALID_FOR, true)
        String nodeDetails = node.details as String
        checkScopePromptTitle(node, 'pgm', true, 'rpm.scope.class.Risk.traits.fieldBRisk')
        checkScopePromptDropdown(node, 'pgm', 'pgm1', ['pgm3'], ['pgm1', 'pgm2', DEFAULT])
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'div')
        checkNoScopePrompt(node, 'state')
        assert node.scope == new CaseInsensitiveMap()

        //ACoverage has no scope prompts
        checkNode('ACoverage', 'Coverage')
        checkNoScopePrompt(node)
        assert node.scope == [pgm: 'pgm1', div: 'div1', coverage: 'ACoverage', risk: 'ARisk', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap

        //BCoverage has one required scope prompt since requires div=div3.
        checkNode('BCoverage', 'Coverage', REQUIRED_SCOPE_VALUE_NOT_FOUND_FOR, IS_NOT_VALID_FOR, true)
        nodeDetails = node.details as String
        checkScopePromptTitle(node, 'div', true, 'rpm.scope.class.Coverage.traits.fieldBCoverage')
        checkScopePromptDropdown(node, 'div', 'div1', ['div3'], ['div1', 'div2', DEFAULT])
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'pgm')
        checkNoScopePrompt(node, 'state')
        assert node.scope == new CaseInsensitiveMap()

        //CCoverage has one default scope prompt since it doesn't have OH as an optional scope value.
        checkNode('CCoverage', 'Coverage', '', DEFAULTS_WERE_USED, false)
        nodeDetails = node.details as String
        checkScopePromptTitle(node, 'state', false, 'rpm.scope.class.Coverage.traits.fieldCCoverage')
        checkScopePromptDropdown(node, 'state', 'OH', ['GA', 'IN', 'NY', DEFAULT], ['KY', 'OH'])
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'div')
        checkNoScopePrompt(node, 'pgm')
        assert node.scope == [state: 'OH', coverage: 'CCoverage', risk: 'ARisk', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
    }

    @Test
    void testLoadGraph_scopePrompt_nodes_afterProductSelected_afterOptionalGraphScopeSelected_twice()
    {
        //Load graph with no scope
        String startCubeName = 'rpm.class.Product'
        topNodeScope = new CaseInsensitiveMap()
        Map options = [startCubeName: startCubeName, scope: topNodeScope]
        Map node = loadGraph(options)

        //User picks AProduct. Reload.
        topNodeScope.product = 'AProduct'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)
        assert 8 == nodes.size()
        assert 7 == edges.size()

        //User picks pgm = pgm1, state = OH and div = div1. Reload after each.
        topNodeScope = new CaseInsensitiveMap(scopeInfo.scope as Map)
        topNodeScope.pgm = 'pgm1'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)

        topNodeScope = new CaseInsensitiveMap(scopeInfo.scope as Map)
        topNodeScope.state = 'OH'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)

        topNodeScope = new CaseInsensitiveMap(scopeInfo.scope as Map)
        topNodeScope.div = 'div1'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)

        //User changes to div = div3. Reload.
        topNodeScope = new CaseInsensitiveMap(scopeInfo.scope as Map)
        topNodeScope.div = 'div3'
        options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
        node = loadGraph(options)
        assert 8 == nodes.size()
        assert 7 == edges.size()

        //AProduct has no scope prompt
        checkNode('AProduct', 'Product')
        checkNoScopePrompt(node)
        assert node.scope == [ product: 'AProduct',_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap

        //Product.Risks has default scope prompt since it doesn't have div3 as an optional scope value.
        checkEnumNode("${VALID_VALUES_FOR_FIELD_SENTENCE_CASE}Risks on AProduct", '', false)
        String nodeDetails = node.details as String
        checkScopePromptTitle(node, 'div', false, 'rpm.scope.enum.Product.Risks.traits.exists')
        checkScopePromptDropdown(node, 'div', 'div3', ['div1', 'div2', DEFAULT], ['div3'])
        checkNoScopePrompt(node, 'state')
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'pgm')
        assert node.scope == [div: 'div3', sourceFieldName: 'Risks', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap

        //ARisk has default scope prompt since it doesn't have div3 as an optional scope value.
        checkNode('ARisk', 'Risk', '', DEFAULTS_WERE_USED, false)
        nodeDetails = node.details as String
        checkScopePromptTitle(node, 'div', false, 'rpm.scope.class.Risk.traits.fieldARisk')
        checkScopePromptDropdown(node, 'div', 'div3', ['div1', DEFAULT], ['div2', 'div3'])
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'pgm')
        checkNoScopePrompt(node, 'state')
        assert node.scope == [div: 'div3', state: 'OH', risk: 'ARisk', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap

        //BRisk has required scope prompt since requires pgm=pgm3
        checkNode('BRisk', 'Risk', REQUIRED_SCOPE_VALUE_NOT_FOUND_FOR, IS_NOT_VALID_FOR, true)
        nodeDetails = node.details as String
        checkScopePromptTitle(node, 'pgm', true, 'rpm.scope.class.Risk.traits.fieldBRisk')
        checkScopePromptDropdown(node, 'pgm', 'pgm1', ['pgm3'], ['pgm1', 'pgm2', DEFAULT])
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'div')
        checkNoScopePrompt(node, 'state')
        assert node.scope == new CaseInsensitiveMap()

        //ACoverage has required scope prompt since requires div1 or div2
        checkNode('ACoverage', 'Coverage', REQUIRED_SCOPE_VALUE_NOT_FOUND_FOR, IS_NOT_VALID_FOR, true)
        nodeDetails = node.details as String
        checkScopePromptTitle(node, 'div', true, 'rpm.scope.class.Coverage.traits.fieldACoverage')
        checkScopePromptDropdown(node, 'div', 'div3', ['div1', 'div2'], ['div3', DEFAULT])
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'pgm')
        checkNoScopePrompt(node, 'state')
        assert node.scope == new CaseInsensitiveMap()

        //BCoverage has one default scope prompt since it doesn't have OH as an optional scope value.
        checkNode('BCoverage', 'Coverage', '', DEFAULTS_WERE_USED, false)
        nodeDetails = node.details as String
        checkScopePromptTitle(node, 'state', false, 'rpm.scope.class.Coverage.traits.fieldBCoverage')
        checkScopePromptDropdown(node, 'state', 'OH', ['KY', 'IN', 'NY', DEFAULT], ['GA', 'OH'])
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'div')
        checkNoScopePrompt(node, 'pgm')
        assert node.scope == [div: 'div3', state: 'OH', coverage: 'BCoverage', risk: 'ARisk', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap

        //CCoverage has one default scope prompt since it doesn't have OH as an optional scope value.
        checkNode('CCoverage', 'Coverage', '', DEFAULTS_WERE_USED, false)
        nodeDetails = node.details as String
        checkScopePromptTitle(node, 'state', false, 'rpm.scope.class.Coverage.traits.fieldCCoverage')
        checkScopePromptDropdown(node, 'state', 'OH', ['GA', 'IN', 'NY', DEFAULT], ['KY', 'OH'])
        checkNoScopePrompt(node, 'product')
        checkNoScopePrompt(node, 'div')
        checkNoScopePrompt(node, 'pgm')
        assert node.scope == [state: 'OH', coverage: 'CCoverage', risk: 'ARisk', product: 'AProduct', _effectiveVersion: ApplicationID.DEFAULT_VERSION, policyControlDate: defaultScopeDate, quoteDate: defaultScopeDate] as CaseInsensitiveMap
    }

    @Test
    void testLoadGraph_scopePrompt_graph_initial_nonEPM()
    {
        String startCubeName = 'rpm.class.partyrole.LossPrevention'
        topNodeScope = new CaseInsensitiveMap()
        Map options = [startCubeName: startCubeName, scope: topNodeScope]

        Map node = loadGraph(options)
        assert nodes.size() == 4
        assert edges.size() == 3

        assert scopeInfo.scope == [_effectiveVersion: ApplicationID.DEFAULT_VERSION]
        //TODO: assert scopeInfo.scopeMessage.contains('Reset scope')
        checkGraphScopeNonEPM()
    }

    @Test
    void testLoadGraph_scopePrompt_nodes_initial_nonEPM()
    {
        String startCubeName = 'rpm.class.partyrole.LossPrevention'
        topNodeScope = new CaseInsensitiveMap()
        Map options = [startCubeName: startCubeName, scope: topNodeScope]

        Map node = loadGraph(options)
        assert nodes.size() == 4
        assert edges.size() == 3

        //partyrole.LossPrevention has no scope prompt
        checkNode('partyrole.LossPrevention', 'partyrole.LossPrevention')
        checkNoScopePrompt(node)
        assert node.availableScope == [_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap
        assert node.scope == [_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap
    }

    @Test
    void testLoadGraph_scopePrompt_missingRequiredScope_nonEPM()
    {
        NCube cube = NCubeManager.getCube(appId, 'rpm.class.party.ProfitCenter')
        try
        {
            //Change cube to have declared required scope
            cube.setMetaProperty('requiredScopeKeys', ['dummyRequiredScopeKey'])
            String startCubeName = 'rpm.class.partyrole.LossPrevention'
            topNodeScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap
            Map options = [startCubeName: startCubeName, scope: topNodeScope]

            Map node = loadGraph(options)
            //TODO:  String scopeMessage = scopeInfo.scopeMessage

            //Check graph scope prompt
            //TODO:  assert 0 == scopeInfo.optionalGraphScopeAvailableValues.dummyRequiredScopeKey.size()
            //TODO: assert 1 == scopeInfo.optionalGraphScopeCubeNames.dummyRequiredScopeKey.size()
            //TODO: assert ['rpm.class.party.ProfitCenter'] as Set== scopeInfo.optionalGraphScopeCubeNames.dummyRequiredScopeKey as Set
            //TODO: Add check for highlighted class
            //TODO: assert scopeMessage.contains("""<input id="dummyRequiredScopeKey" value="" placeholder="Enter value..." class="scopeInput form-control """)
            //TODO: assert !scopeMessage.contains('<li id="dummyRequiredScopeKey"')

            //Check node scope prompt
            checkNode('party.ProfitCenter', 'party.ProfitCenter', ADDITIONAL_SCOPE_REQUIRED_FOR, ADDITIONAL_SCOPE_REQUIRED, true)
            String nodeDetails = node.details as String
            checkScopePromptTitle(node, 'dummyRequiredScopeKey', true, 'rpm.class.party.ProfitCenter')
            checkScopePromptDropdown(node, 'dummyRequiredScopeKey', '', [], [])
            assert node.scope == new CaseInsensitiveMap()
            assert node.availableScope == [sourceFieldName: 'Parties', _effectiveVersion: ApplicationID.DEFAULT_VERSION] as CaseInsensitiveMap
        }
        finally
        {
            //Reset cube
            cube.removeMetaProperty('requiredScopeKeys')
        }
    }

    @Test
    void testLoadGraph_scopePrompt_missingDeclaredRequiredScope()
    {
        NCube cube = NCubeManager.getCube(appId, 'rpm.class.Coverage')
        try
        {
            //Change cube to have declared required scope
            cube.setMetaProperty('requiredScopeKeys', ['dummyRequiredScopeKey'])
            Map scope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                         policyControlDate: '2017-01-01',
                         quoteDate        : '2017-01-01',
                         risk             : 'WProductOps']

            Map availableNodeScope = new CaseInsensitiveMap(scope)
            availableNodeScope.putAll([sourceFieldName: 'Coverages', risk: 'StateOps', sourceRisk: 'WProductOps', coverage: 'CCCoverage'])

            String startCubeName = 'rpm.class.Risk'
            topNodeScope = new CaseInsensitiveMap(scope)
            Map options = [startCubeName: startCubeName, scope: topNodeScope]
            Map node = loadGraph(options)
            //TODO:  String scopeMessage = scopeInfo.scopeMessage

            //Check graph scope prompt
            //TODO: assert 0 == scopeInfo.optionalGraphScopeAvailableValues.dummyRequiredScopeKey.size()
            //TODO: assert 1 == scopeInfo.optionalGraphScopeCubeNames.dummyRequiredScopeKey.size()
            //TODO:  assert ['rpm.class.Coverage'] as Set== scopeInfo.optionalGraphScopeCubeNames.dummyRequiredScopeKey as Set
            //TODO: Add check for highlighted class
            //TODO: assert scopeMessage.contains("""<input id="dummyRequiredScopeKey" value="" placeholder="Enter value..." class="scopeInput form-control """)
            //TODO: assert !scopeMessage.contains('<li id="dummyRequiredScopeKey"')

            //Check node scope prompt
            checkNode('CCCoverage', 'Coverage', ADDITIONAL_SCOPE_REQUIRED_FOR, ADDITIONAL_SCOPE_REQUIRED, true)
            String nodeDetails = node.details as String
            checkScopePromptTitle(node, 'dummyRequiredScopeKey', true, 'rpm.class.Coverage')
            checkScopePromptDropdown(node, 'dummyRequiredScopeKey', '', [], [])
            assert node.scope == new CaseInsensitiveMap()
            assert node.availableScope == availableNodeScope
        }
        finally
        {
            //Reset cube
            cube.removeMetaProperty('requiredScopeKeys')
        }
    }

    @Test
    void testGetCellValues_classNode_show_missingRequiredScope()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                             coverage         : 'TCoverage'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.class.Coverage'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options)
        checkNode('TCoverage', 'Coverage')
      
        //Simulate that the user clicks Show Traits for the node.
        //Required node scope prompt now shows for points, but not yet one for businessDivision code
        node.showCellValues = true
        node.showingHidingCellValues = true
        node = loadNodeDetails(node)
        checkNode('TCoverage', 'Coverage', '', ADDITIONAL_SCOPE_REQUIRED, true, true)
        checkScopePromptTitle(node, 'points', true, 'rpm.scope.class.Coverage.traits.fieldTCoverage')
        checkScopePromptDropdown(node, 'points', '', ['A', 'B', 'C'], [DEFAULT], true)
        checkNoScopePrompt(node, 'businessDivisionCode')
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope

        //Simulate that the user picks points = A in the node scope prompt.
        //Both required scope points and unbound node scope prompt businessDivisionCode are now showing.
        utilizedScope.points = 'A'
        availableScope.points = 'A'
        node.availableScope = new CaseInsensitiveMap(availableScope)
        node.showingHidingCellValues = false
        node = loadScopeChange(node)
        checkNode('TCoverage', 'Coverage', '', DEFAULTS_WERE_USED, false, true)
        checkScopePromptTitle(node, 'points', true, 'rpm.scope.class.Coverage.traits.fieldTCoverage')
        checkScopePromptDropdown(node, 'points', 'A', ['A', 'B', 'C'], [DEFAULT], true)
        checkScopePromptTitle(node, 'businessDivisionCode', false, 'rpm.scope.class.Coverage.traits.StatCode')
        checkScopePromptDropdown(node, 'businessDivisionCode', DEFAULT, ['AAADIV', 'BBBDIV', DEFAULT], [], true)
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope

        //Simulate that the user picks businessDivisionCode = AAADIV in the node scope prompt.
        //Both scope prompts are still showing.
        utilizedScope.businessDivisionCode = 'AAADIV'
        availableScope.businessDivisionCode = 'AAADIV'
        node.availableScope = new CaseInsensitiveMap(availableScope)
        node = loadScopeChange(node)
        checkNode('TCoverage', 'Coverage', '', DEFAULTS_WERE_USED, false, true)
        checkScopePromptTitle(node, 'points', true, 'rpm.scope.class.Coverage.traits.fieldTCoverage')
        checkScopePromptDropdown(node, 'points', 'A', ['A', 'B', 'C'], [DEFAULT], true)
        checkScopePromptTitle(node, 'businessDivisionCode', false, 'rpm.scope.class.Coverage.traits.StatCode')
        checkScopePromptDropdown(node, 'businessDivisionCode', 'AAADIV', ['AAADIV', 'BBBDIV', DEFAULT], [], true)
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope
    }

    @Test
    void testGetCellValues_classNode_show_invalidRequiredScope()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                             coverage         : 'TCoverage'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope
        
        //Load graph
        String startCubeName = 'rpm.class.Coverage'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options)
        checkNode('TCoverage', 'Coverage')
        checkNoScopePrompt(node, 'points')

        //Simulate that the user clicks Show Traits for the node.
        //Required node scope prompt now shows for points.
        node.showCellValues = true
        node.showingHidingCellValues = true
        node = loadNodeDetails(node)
        checkNode('TCoverage', 'Coverage', '', ADDITIONAL_SCOPE_REQUIRED, true, true)
        checkScopePromptTitle(node, 'points', true, 'rpm.scope.class.Coverage.traits.fieldTCoverage')
        checkScopePromptDropdown(node, 'points', '', ['A', 'B', 'C'], [DEFAULT], true)
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope

        //Simulate that the user enters points = bogus in the node scope prompt.
        //Invalid node scope prompt should now show for points.
        availableScope.points = 'bogus'
        node.availableScope = new CaseInsensitiveMap(availableScope)
        node = loadScopeChange(node)
        checkNode('TCoverage', 'Coverage', REQUIRED_SCOPE_VALUE_NOT_FOUND_FOR, IS_NOT_VALID_FOR, true, true)
        checkScopePromptTitle(node, 'points', true, 'rpm.scope.class.Coverage.traits.fieldTCoverage')
        checkScopePromptDropdown(node, 'points', 'bogus', ['A', 'B', 'C'], [DEFAULT], true)
        assert utilizedScope == node.scope
        assert availableScope == node.availableScope
    }


    @Test
    void testLoadGraph_scopePrompt_enumWithSingleDefaultValue()
    {
        String startCubeName = 'rpm.class.Product'
        Map scope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                     product:'BProduct',
                     policyControlDate:'2017-01-01',
                     quoteDate:'2017-01-01']
        topNodeScope = new CaseInsensitiveMap(scope)

        Map nodeScope = new CaseInsensitiveMap(scope)
        nodeScope.risk = 'DRisk'

        Map availableScope = new CaseInsensitiveMap(nodeScope)
        availableScope.sourceFieldName = 'Risks'

        Map options = [startCubeName: startCubeName, scope: topNodeScope]
        Map node = loadGraph(options)
        assert 4 == nodes.size()
        assert 3 == edges.size()

        //The edge for field Risks from BProduct to enum Product.Risks
        Map edge = edges.values().find { Map edge -> 'BProduct' == edge.fromName && 'Product.Risks' == edge.toName}
        assert 'Risks' == edge.label
        assert "Field Risks cardinality ${V_MIN_CARDINALITY}:${V_MAX_CARDINALITY}".toString() == edge.title

        checkNode('DRisk', 'Risk', '', DEFAULTS_WERE_USED, false)
        String nodeDetails = node.details as String
        checkScopePromptTitle(node, 'state', false, 'rpm.scope.class.Risk.traits.fieldDRisk')
        checkScopePromptDropdown(node, 'state', 'Default', [DEFAULT], [])

        assert node.availableScope == availableScope
        assert node.scope == nodeScope
    }

    @Test
    void testLoadGraph_scopePrompt_enumWithMissingRequiredScope()
    {
        String startCubeName = 'rpm.class.Risk'
        Map scope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                     risk: 'DRisk',
                     policyControlDate:'2017-01-01',
                     quoteDate:'2017-01-01'] as CaseInsensitiveMap
        topNodeScope = new CaseInsensitiveMap(scope)

        Map availableScope = new CaseInsensitiveMap(scope)
        availableScope.sourceFieldName = 'Coverages'

        Map options = [startCubeName: startCubeName, scope: topNodeScope]
        Map node = loadGraph(options)
        assert 2 == nodes.size()
        assert 1 == edges.size()

        //The edge for field Coverages from DRisk to enum Risk.Coverages
        Map edge = edges.values().find { Map edge -> 'DRisk' == edge.fromName && 'Risk.Coverages' == edge.toName}
        assert "${ADDITIONAL_SCOPE_REQUIRED_FOR}Coverages".toString() == edge.label
        assert "Field Coverages cardinality ${V_MIN_CARDINALITY}:${V_MAX_CARDINALITY}".toString() == edge.title

        //Risk.Coverages enum has one required prompt
        checkEnumNode("${ADDITIONAL_SCOPE_REQUIRED_FOR}${VALID_VALUES_FOR_FIELD_LOWER_CASE}Coverages on DRisk", ADDITIONAL_SCOPE_REQUIRED, true)
        String nodeDetails = node.details as String
        checkScopePromptTitle(node, 'pgm', true, 'rpm.scope.enum.Risk.Coverages.traits.exists')
        checkScopePromptDropdown(node, 'pgm', '', ['pgm1', 'pgm2', 'pgm3'], [DEFAULT])
        checkNoScopePrompt(node, 'state')

        assert node.availableScope == availableScope
        assert node.scope == new CaseInsensitiveMap()
    }

    @Test
    void testLoadGraph_scopePrompt_enumWithInvalidRequiredScope()
    {
        Map availableScope = defaultRpmScope + [risk: 'DRisk']

        Map enumUtilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                                 risk             : 'DRisk',
                                 sourceFieldName  : 'Coverages'] as CaseInsensitiveMap
        Map enumAvailableScope = defaultRpmScope + enumUtilizedScope

        //Load graph
        String startCubeName = 'rpm.class.Risk'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        loadGraph(options)

        //The edge for field Coverages from DRisk to enum Risk.Coverages has missing scope label.
        Map edge = edges.values().find { Map edge -> 'DRisk' == edge.fromName && 'Risk.Coverages' == edge.toName}
        assert "${ADDITIONAL_SCOPE_REQUIRED_FOR}Coverages".toString() == edge.label
        assert "Field Coverages cardinality ${V_MIN_CARDINALITY}:${V_MAX_CARDINALITY}".toString() == edge.title

        //Risk.Coverages enum has one required prompt
        String enumNodeTitle = "${ADDITIONAL_SCOPE_REQUIRED_FOR}${VALID_VALUES_FOR_FIELD_LOWER_CASE}Coverages on DRisk"
        Map node = nodes.values().find {Map node1 ->  enumNodeTitle == node1.title}
        node = loadNodeDetails(node)
        checkEnumNode(enumNodeTitle, ADDITIONAL_SCOPE_REQUIRED, true)
        checkScopePromptTitle(node, 'pgm', true, 'rpm.scope.enum.Risk.Coverages.traits.exists')
        checkScopePromptDropdown(node, 'pgm', '', ['pgm1', 'pgm2', 'pgm3'], [DEFAULT])
        assert enumUtilizedScope == node.scope
        assert enumAvailableScope == node.availableScope

        //Simulate that the user enters invalid pgm = pgm4 in the node scope prompt.
        enumAvailableScope.pgm = 'pgm4'
        node.availableScope = new CaseInsensitiveMap(enumAvailableScope)
        node = loadScopeChange(node)

        //The edge for field Coverages from DRisk to enum Risk.Coverages has invalid scope label.
        edge = edges.values().find { Map edge1 -> 'DRisk' == edge1.fromName && 'Risk.Coverages' == edge1.toName}
        assert "${REQUIRED_SCOPE_VALUE_NOT_FOUND_FOR}Coverages".toString() == edge.label
        assert "Field Coverages cardinality ${V_MIN_CARDINALITY}:${V_MAX_CARDINALITY}".toString() == edge.title

        //Risk.Coverages enum has one required prompt
        checkEnumNode("${REQUIRED_SCOPE_VALUE_NOT_FOUND_FOR}${VALID_VALUES_FOR_FIELD_LOWER_CASE}Coverages on DRisk", IS_NOT_VALID_FOR, true)
        checkScopePromptTitle(node, 'pgm', true, 'rpm.scope.enum.Risk.Coverages.traits.exists')
        checkScopePromptDropdown(node, 'pgm', 'pgm4', ['pgm1', 'pgm2', 'pgm3'], [DEFAULT])
        enumUtilizedScope.pgm = 'pgm4'
        assert enumUtilizedScope == node.scope
        assert enumAvailableScope == node.availableScope

        //Simulate that the user enters valid pgm = pgm1 in the node scope prompt.
        enumAvailableScope.pgm = 'pgm1'
        node.availableScope = new CaseInsensitiveMap(enumAvailableScope)
        node = loadScopeChange(node)

        //The edge for field Coverages from DRisk to enum Risk.Coverages has no label.
        edge = edges.values().find { Map edge1 -> 'DRisk' == edge1.fromName && 'Risk.Coverages' == edge1.toName}
        assert 'Coverages' == edge.label
        assert "Field Coverages cardinality ${V_MIN_CARDINALITY}:${V_MAX_CARDINALITY}".toString() == edge.title

        //Risk.Coverages enum has one required prompt
        checkEnumNode("${VALID_VALUES_FOR_FIELD_SENTENCE_CASE}Coverages on DRisk")
        checkScopePromptTitle(node, 'pgm', true, 'rpm.scope.enum.Risk.Coverages.traits.exists')
        checkScopePromptDropdown(node, 'pgm', 'pgm1', ['pgm1', 'pgm2', 'pgm3'], [DEFAULT])
        enumUtilizedScope.pgm = 'pgm1'
        assert enumUtilizedScope == node.scope
        assert enumAvailableScope == node.availableScope
    }

    @Test
    void testLoadGraph_scopePrompt_derivedScopeKey_topNode()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                             risk             : 'StateOps'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.class.Risk'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        Map node = loadGraph(options)

        //Check that sourceRisk is part of node scope
        List<String> risks = ['ARisk', 'BRisk', 'DRisk', 'GProductOps', 'GStateOps', 'ProductLocation', 'StateOps', 'WProductOps']
        checkNode('StateOps', 'Risk', ADDITIONAL_SCOPE_REQUIRED_FOR, ADDITIONAL_SCOPE_REQUIRED, true)
        checkScopePromptTitle(node, 'sourceRisk', true, 'rpm.scope.class.Risk.traits.Risks')
        checkScopePromptDropdown(node, 'sourceRisk', '', risks, [DEFAULT])
    }

    @Test
    void testLoadGraph_scopePrompt_derivedScopeKey_notTopNode()
    {
        Map utilizedScope = [_effectiveVersion: ApplicationID.DEFAULT_VERSION,
                             product          : 'WProduct',
                             risk             : 'WProductOps'] as CaseInsensitiveMap
        Map availableScope = defaultRpmScope + utilizedScope

        //Load graph
        String startCubeName = 'rpm.class.Risk'
        Map options = [startCubeName: startCubeName, scope: new CaseInsensitiveMap(availableScope)]
        loadGraph(options)

        //Check that sourceRisk is not a scope prompt
        Map node = nodes.values().find { Map node1 -> 'StateOps' == node1.label }
        node = loadNodeDetails(node)
        checkNode('StateOps', 'Risk')
        checkNoScopePrompt(node, 'sourceRisk')
    }

    /* TODO: Will revisit providing "in scope" available scope values for r:exists at a later time.
   @Test
   void testLoadGraph_inScopeScopeValues_unboundAxis()
   {
       //Load graph with no scope
       String startCubeName = 'rpm.class.Product'
       topNodeScope = new CaseInsensitiveMap()
       Map options = [startCubeName: startCubeName, scope: topNodeScope]
       Map node = loadGraph(options)
       assert 1 == nodes.size()

       //User picks GProduct. Reload. This will result in unboundAxis on div.
       topNodeScope.product = 'GProduct'
       options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
       Map node = loadGraph(options)
       assert 2 == nodes.size()

       //Check node scope prompt
       Map node = checkEnumNodeBasics("${VALID_VALUES_FOR_FIELD_SENTENCE_CASE}Risks on GProduct")
       checkScopePromptDropdown(node, 'div', 'Default', ['div1', 'div2', DEFAULT], [])
   }

   @Test
   void testLoadGraph_inScopeScopeValues_invalidCoordinate()
   {
       //Load graph with no scope
       String startCubeName = 'rpm.class.Product'
       topNodeScope = new CaseInsensitiveMap()
       Map options = [startCubeName: startCubeName, scope: topNodeScope]
       Map node = loadGraph(options)
       assert 1 == nodes.size()

       //User picks GProduct. Reload.
       topNodeScope.product = 'GProduct'
       options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
       Map node = loadGraph(options)
       assert 2 == nodes.size()

       //User picks div = div1. Reload. This will result in InvalidCoordinateException due to missing category scope.
       topNodeScope.div = 'div1'
       options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
       Map node = loadGraph(options)
       assert 2 == nodes.size()

       //Check graph scope prompt
       assert 5 == scopeInfo.optionalGraphScopeAvailableValues.category.size()
       assert ['cat1', 'cat2', 'cat3', 'cat4', 'cat5'] as Set == scopeInfo.optionalGraphScopeAvailableValues.category as Set

       //Check node scope prompt
       Map node = checkEnumNodeBasics("${ADDITIONAL_SCOPE_REQUIRED_FOR}${VALID_VALUES_FOR_FIELD_LOWER_CASE}Risks on GProduct", ADDITIONAL_SCOPE_REQUIRED, true)
       checkScopePromptDropdown(node, 'category', '', ['cat1', 'cat2', 'cat3', 'cat4', 'cat5'], [DEFAULT], SELECT_OR_ENTER_VALUE)
   }

   @Test
   void testLoadGraph_inScopeScopeValues_coordinateNotFound()
   {
       //Load graph with no scope
       String startCubeName = 'rpm.class.Product'
       topNodeScope = new CaseInsensitiveMap()
       Map options = [startCubeName: startCubeName, scope: topNodeScope]
       Map node = loadGraph(options)
       assert 1 == nodes.size()

       //User picks GProduct. Reload.
       topNodeScope.product = 'GProduct'
       options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
       Map node = loadGraph(options)
       assert 2 == nodes.size()

       //User picks div = div1. Reload. This will result in InvalidCoordinateException since category is required scope.
       topNodeScope.div = 'div1'
       options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
       Map node = loadGraph(options)
       assert 2 == nodes.size()

       //User picks category = catBogus. Reload. This will result in CoordinateNotFoundException since catBogus doesn't exist.
       topNodeScope.category = 'catBogus'
       options = [startCubeName: startCubeName, scope: topNodeScope, visInfo: visInfo]
       Map node = loadGraph(options)
       assert 2 == nodes.size()

       //Check graph scope prompt for category
       assert 5 == scopeInfo.optionalGraphScopeAvailableValues.category.size()
       assert ['cat1', 'cat2', 'cat3', 'cat4', 'cat5'] as Set == scopeInfo.optionalGraphScopeAvailableValues.category as Set

       //Check node scope prompt
       Map node = checkEnumNodeBasics("${REQUIRED_SCOPE_VALUE_NOT_FOUND_FOR}${VALID_VALUES_FOR_FIELD_LOWER_CASE}Risks on GProduct", IS_NOT_VALID_FOR, true)
       checkScopePromptDropdown(node, 'category', '', ['cat1', 'cat2', 'cat3', 'cat4',  'cat5'], [DEFAULT,], SELECT_OR_ENTER_VALUE)
   }
   */


    //*************************************************************************************

    private Map loadGraph(Map options, boolean hasMessages = false)
    {
        visualizer = new RpmVisualizer()
        visInfo?.nodes = [:]
        visInfo?.edges = [:]
        Map returnMap = visualizer.loadGraph(appId, options)
        visInfo = returnMap.visInfo as RpmVisualizerInfo
        messages = visInfo.messages
        if (!hasMessages)
        {
            assert !messages
        }
        nodes = visInfo.nodes as Map
        edges = visInfo.edges as Map
        return nodes[1l]
    }

    private Map loadScopeChange(Map node, boolean hasMessages = false)
    {
        visInfo.selectedNodeId = node.id as Long
        Map returnMap = visualizer.loadScopeChange(appId, [visInfo: visInfo])
        visInfo = returnMap.visInfo as RpmVisualizerInfo
        messages = visInfo.messages
        if (!hasMessages)
        {
            assert !messages
        }
        nodes = visInfo.nodes as Map
        edges = visInfo.edges as Map
        return nodes[node.id as Long]
    }

    private Map loadNodeDetails(Map node, boolean hasMessages = false)
    {
        visInfo.selectedNodeId = node.id as Long
        Map returnMap = visualizer.loadNodeDetails(appId, [visInfo: visInfo])
        visInfo = returnMap.visInfo as RpmVisualizerInfo
        messages = visInfo.messages
        if (!hasMessages)
        {
            assert !messages
        }
        nodes = visInfo.nodes as Map
        edges = visInfo.edges as Map
        return nodes[node.id as Long]
    }

    private void checkTopNodeScope(String selectedProductName = '')
    {

        Set<String> scopeKeys = ['policyControlDate', 'quoteDate', '_effectiveVersion', 'product'] as CaseInsensitiveSet
        Set<String> products = ['AProduct', 'BProduct', 'GProduct', 'UProduct', 'WProduct'] as CaseInsensitiveSet

       /* assert 4 == scopeInfo.availableScopeValues.keySet().size()
        assert scopeInfo.availableScopeValues.keySet().containsAll(scopeKeys)
        assert 0 == scopeInfo.availableScopeValues.policyControlDate.size()
        assert 0 == scopeInfo.availableScopeValues.quoteDate.size()
        assert 1 == scopeInfo.availableScopeValues._effectiveVersion.size()
        assert [ApplicationID.DEFAULT_VERSION] as Set == scopeInfo.availableScopeValues._effectiveVersion as Set
        assert 5 == scopeInfo.availableScopeValues.product.size()
        assert scopeInfo.availableScopeValues.product.containsAll(products)

        assert 4 == scopeInfo.scopeCubeNames.keySet().size()
        assert scopeInfo.scopeCubeNames.keySet().containsAll(scopeKeys)
        assert 0 == scopeInfo.scopeCubeNames.policyControlDate.size()
        assert 0 == scopeInfo.scopeCubeNames.quoteDate.size()
        assert 0 == scopeInfo.scopeCubeNames._effectiveVersion.size()
        assert 1 == scopeInfo.scopeCubeNames.product.size()
        assert ['rpm.scope.class.Product.traits'] as Set== scopeInfo.scopeCubeNames.product as Set

        String scopeMessage = scopeInfo.scopeMessage
        assert scopeMessage.contains(selectedProductName + SCOPE_UTILIZED_BY_TOP_NODE)
        checkScopePromptTitle(scopeMessage, 'policyControlDate', true, null, 'topNode')
        checkScopePromptTitle(scopeMessage, 'quoteDate', true, null, 'topNode')
        checkScopePromptTitle(scopeMessage, '_effectiveVersion', true, null, 'topNode')
        checkScopePromptTitle(scopeMessage, 'product', true, null, 'topNode')
        checkScopePromptDropdown(scopeMessage, 'policyControlDate', "${defaultScopeDate}", [], [DEFAULT], ENTER_VALUE)
        checkScopePromptDropdown(scopeMessage, 'quoteDate', "${defaultScopeDate}", [], [DEFAULT], ENTER_VALUE)
        checkScopePromptDropdown(scopeMessage, '_effectiveVersion', "${ApplicationID.DEFAULT_VERSION}", [], [DEFAULT], SELECT_OR_ENTER_VALUE)
        checkScopePromptDropdown(scopeMessage, 'product', "${selectedProductName}", products as List, [DEFAULT], SELECT_OR_ENTER_VALUE)*/
    }

    private void checkOptionalGraphScope(String selectedProductName = '', String selectedPgmName = '', String selectedStateName = 'Default', selectedDivName = 'Default', boolean includeStateNM = false)
    {
        //TODO:
      /*  Set<String> scopeKeys = ['pgm', 'state', 'div'] as CaseInsensitiveSet
        String scopeMessage = scopeInfo.scopeMessage

        if (selectedProductName)
        {
            assert scopeMessage.contains(OPTIONAL_SCOPE_IN_VISUALIZATION)
            assert 3 == scopeInfo.optionalGraphScopeAvailableValues.keySet().size()
            assert scopeInfo.optionalGraphScopeAvailableValues.keySet().containsAll(scopeKeys)
            assert 3 == scopeInfo.optionalGraphScopeCubeNames.keySet().size()
            assert scopeInfo.optionalGraphScopeCubeNames.keySet().containsAll(scopeKeys)

            //Check pgm
            checkScopePromptTitle(scopeMessage, 'pgm', false, null, 'additionalGraphScope')
            assert 3 == scopeInfo.optionalGraphScopeAvailableValues.pgm.size()
            assert ['pgm1', 'pgm2', 'pgm3'] as Set == scopeInfo.optionalGraphScopeAvailableValues.pgm as Set
            assert 2 == scopeInfo.optionalGraphScopeCubeNames.pgm.size()
            assert ['rpm.scope.class.Risk.traits.fieldBRisk', 'rpm.scope.class.Coverage.traits.fieldACoverage'] as Set == scopeInfo.optionalGraphScopeCubeNames.pgm as Set
            checkScopePromptDropdown(scopeMessage, 'pgm', "${selectedPgmName}", ['pgm1', 'pgm2', 'pgm3'], [DEFAULT], SELECT_OR_ENTER_VALUE)

            //Check div
            checkScopePromptTitle(scopeMessage, 'div', false, null, 'additionalGraphScope')
            if ('div3' == selectedDivName)
            {
                checkScopePromptDropdown(scopeMessage, 'div', "${selectedDivName}", ['div1', 'div2', DEFAULT], ['div3'], SELECT_OR_ENTER_VALUE)
                assert 3 == scopeInfo.optionalGraphScopeAvailableValues.div.size()
                assert [null, 'div1', 'div2'] as Set == scopeInfo.optionalGraphScopeAvailableValues.div as Set
                assert 3 == scopeInfo.optionalGraphScopeCubeNames.div.size()
                assert ['rpm.scope.enum.Product.Risks.traits.exists', 'rpm.scope.class.Risk.traits.fieldARisk', 'rpm.scope.class.Coverage.traits.fieldACoverage'] as Set == scopeInfo.optionalGraphScopeCubeNames.div as Set

            }
            else
            {
                checkScopePromptDropdown(scopeMessage, 'div', "${selectedDivName}", ['div1', 'div2', 'div3', DEFAULT], [], SELECT_OR_ENTER_VALUE)
                assert 4 == scopeInfo.optionalGraphScopeAvailableValues.div.size()
                assert [null, 'div1', 'div2', 'div3'] as Set == scopeInfo.optionalGraphScopeAvailableValues.div as Set
                if ('div1' == selectedDivName)
                {
                    assert 2 == scopeInfo.optionalGraphScopeCubeNames.div.size()
                    assert ['rpm.scope.enum.Product.Risks.traits.exists', 'rpm.scope.class.Coverage.traits.fieldBCoverage'] as Set == scopeInfo.optionalGraphScopeCubeNames.div as Set
                }
                else
                {
                    assert 4 == scopeInfo.optionalGraphScopeCubeNames.div.size()
                    assert ['rpm.scope.enum.Product.Risks.traits.exists', 'rpm.scope.class.Risk.traits.fieldARisk', 'rpm.scope.class.Coverage.traits.fieldACoverage', 'rpm.scope.class.Coverage.traits.fieldBCoverage'] as Set == scopeInfo.optionalGraphScopeCubeNames.div as Set
                }
            }

            //Check state
            checkScopePromptTitle(scopeMessage, 'state', false, null, 'additionalGraphScope')
            if (includeStateNM)
            {
                assert 7 == scopeInfo.optionalGraphScopeAvailableValues.state.size()
                assert [null, 'KY', 'NY', 'OH', 'GA', 'IN', 'NM'] as Set == scopeInfo.optionalGraphScopeAvailableValues.state as Set
                assert 3 == scopeInfo.optionalGraphScopeCubeNames.state.size()
                assert ['rpm.scope.class.Risk.traits.fieldARisk', 'rpm.scope.class.Coverage.traits.fieldCCoverage', 'rpm.scope.class.Coverage.traits.fieldBCoverage'] as Set == scopeInfo.optionalGraphScopeCubeNames.state as Set
                checkScopePromptDropdown(scopeMessage, 'state', "${selectedStateName}", ['KY', 'NY', 'OH', 'GA', 'IN', 'NM', DEFAULT], [], SELECT_OR_ENTER_VALUE)
            }
            else
            {
                assert 6 == scopeInfo.optionalGraphScopeAvailableValues.state.size()
                assert [null, 'KY', 'NY', 'OH', 'GA', 'IN'] as Set == scopeInfo.optionalGraphScopeAvailableValues.state as Set
                assert 2 == scopeInfo.optionalGraphScopeCubeNames.state.size()
                assert ['rpm.scope.class.Risk.traits.fieldARisk', 'rpm.scope.class.Coverage.traits.fieldCCoverage'] as Set == scopeInfo.optionalGraphScopeCubeNames.state as Set
                checkScopePromptDropdown(scopeMessage, 'state', "${selectedStateName}", ['KY', 'NY', 'OH', 'GA', 'IN', DEFAULT], ['NM'], SELECT_OR_ENTER_VALUE)
            }
        }
        else
        {
            assert 0 == scopeInfo.optionalGraphScopeAvailableValues.keySet().size()
            assert 0 == scopeInfo.optionalGraphScopeCubeNames.keySet().size()
        }*/
    }

    private void checkGraphScopeNonEPM()
    {
       /* assert 1 == scopeInfo.availableScopeValues.keySet().size()
        assert scopeInfo.availableScopeValues.keySet().contains('_effectiveVersion')
        assert 1 == scopeInfo.availableScopeValues._effectiveVersion.size()
        assert [ApplicationID.DEFAULT_VERSION] as Set == scopeInfo.availableScopeValues._effectiveVersion as Set

        assert 1 == scopeInfo.scopeCubeNames.keySet().size()
        assert scopeInfo.scopeCubeNames.keySet().containsAll('_effectiveVersion')
        assert 0 == scopeInfo.scopeCubeNames._effectiveVersion.size()

        String scopeMessage = scopeInfo.scopeMessage
        assert scopeMessage.contains('partyrole.LossPrevention' + SCOPE_UTILIZED_BY_TOP_NODE )
        assert scopeMessage.contains('title="Scope key _effectiveVersion is required to load partyrole.LossPrevention')
        //TODO: Add check for highlighted class
        assert scopeMessage.contains("""<input id="_effectiveVersion" value="${ApplicationID.DEFAULT_VERSION}" placeholder="Select or enter value..." class="scopeInput form-control """)

        assert 0 == scopeInfo.optionalGraphScopeAvailableValues.keySet().size()
        assert 0 == scopeInfo.optionalGraphScopeCubeNames.keySet().size()*/
    }
    
    private void checkNode(String nodeName, String nodeType, String nodeNamePrefix = '', String nodeDetailsMessage = '', boolean unableToLoad = false, boolean showCellValues = false)
    {
        Map node = nodes.values().find { Map node1 -> "${nodeNamePrefix}${nodeName}".toString() == node1.label }
        checkNodeAndEnumNodeBasics(node, unableToLoad, showCellValues)
        assert nodeType == node.title
        assert nodeType == node.detailsTitle1
        assert (node.details as String).contains(nodeDetailsMessage)
        assert "${nodeNamePrefix}${nodeName}".toString() == node.label
        if (showCellValues)
        {
            assert nodeName == node.detailsTitle2
        }
        else if (nodeName == nodeType)  //No detailsTitle2 for non-EPM classes (i.e. nodeName equals nodeType)
        {
            assert null == node.detailsTitle2
        }
        else
        {
            assert nodeName == node.detailsTitle2
        }
    }

    private void checkEnumNode(String nodeTitle, String nodeDetailsMessage = '', boolean unableToLoad = false, boolean showCellValues = false)
    {
        Map node = nodes.values().find {Map node1 ->  nodeTitle == node1.title}
        checkNodeAndEnumNodeBasics(node, unableToLoad, showCellValues)
        assert null == node.label
        assert nodeTitle == node.detailsTitle1
        assert null == node.detailsTitle2
        assert (node.details as String).contains(nodeDetailsMessage)
    }

    private static void checkNodeAndEnumNodeBasics(Map node, boolean unableToLoad = false, boolean showCellValues = false)
    {
        String nodeDetails = node.details as String
        if (showCellValues && unableToLoad)
        {
            assert nodeDetails.contains("${UNABLE_TO_LOAD}traits")
            assert false == node.showCellValuesLink
            assert false == node.cubeLoaded
            assert true == node.showCellValues
        }
        else if (unableToLoad)
        {
            assert nodeDetails.contains("${UNABLE_TO_LOAD}")
            assert false == node.showCellValuesLink
            assert false == node.cubeLoaded
            assert false == node.showCellValues
        }
        else if (showCellValues)
        {
            assert !nodeDetails.contains("${UNABLE_TO_LOAD}")
            assert true == node.showCellValuesLink
            assert true == node.cubeLoaded
            assert true == node.showCellValues
        }
        else
        {
            assert !nodeDetails.contains("${UNABLE_TO_LOAD}")
            assert true == node.showCellValuesLink
            assert true == node.cubeLoaded
            assert false == node.showCellValues
        }

        if (unableToLoad)
        {
            assert !nodeDetails.contains(DETAILS_LABEL_UTILIZED_SCOPE_WITHOUT_TRAITS)
            assert !nodeDetails.contains(DETAILS_LABEL_UTILIZED_SCOPE_WITH_TRAITS)
            assert nodeDetails.contains(DETAILS_LABEL_AVAILABLE_SCOPE)
            assert !nodeDetails.contains(DETAILS_LABEL_FIELDS)
            assert !nodeDetails.contains(DETAILS_LABEL_FIELDS_AND_TRAITS)
            assert !nodeDetails.contains(DETAILS_LABEL_CLASS_TRAITS)
        }
        else if (showCellValues)
        {
            assert !nodeDetails.contains(UNABLE_TO_LOAD)
            assert !nodeDetails.contains(DETAILS_LABEL_UTILIZED_SCOPE_WITHOUT_TRAITS)
            assert nodeDetails.contains(DETAILS_LABEL_UTILIZED_SCOPE_WITH_TRAITS)
            assert nodeDetails.contains(DETAILS_LABEL_AVAILABLE_SCOPE)
            assert nodeDetails.contains(DETAILS_LABEL_FIELDS_AND_TRAITS)
            assert nodeDetails.contains(DETAILS_LABEL_CLASS_TRAITS)
        }
        else
        {
            assert !nodeDetails.contains(UNABLE_TO_LOAD)
            assert nodeDetails.contains(DETAILS_LABEL_UTILIZED_SCOPE_WITHOUT_TRAITS)
            assert nodeDetails.contains(DETAILS_LABEL_FIELDS)
            assert !nodeDetails.contains(DETAILS_LABEL_FIELDS_AND_TRAITS)
            assert !nodeDetails.contains(DETAILS_LABEL_CLASS_TRAITS)
        }
    }

    private static void checkScopePromptTitle(Map node, String scopeKey, boolean required, String cubeNames = null, String scopeType = null)
    {
        String nodeDetails = node.details as String
        if (required)
        {
            assert nodeDetails.contains("""title="Scope key ${scopeKey} is required to load""")
        }
        else if ('additionalGraphScope' == scopeType)
        {
            assert nodeDetails.contains("Scope key ${scopeKey} is used in the in the visualization. It may be optional for some classes and required by others.")
        }
        else
        {
            assert nodeDetails.contains("""title="Scope key ${scopeKey} is optional to load""")
        }
        if (cubeNames)
        {
            assert nodeDetails.contains(cubeNames)
        }
    }

    private static void checkScopePromptDropdown(Map node, String scopeKey, String selectedScopeValue, List<String> availableScopeValues, List<String> unavailableScopeValues, boolean isTopNode = false)
    {
        String nodeDetails = node.details as String
        String placeHolder = availableScopeValues ? SELECT_OR_ENTER_VALUE : ENTER_VALUE
        String topNodeClass = isTopNode ? DETAILS_CLASS_TOP_NODE : ''
        String typeValueClass = ''
        if (DEFAULT == selectedScopeValue)
        {
            typeValueClass = DETAILS_CLASS_DEFAULT_VALUE
        }
        else if (!availableScopeValues.contains(selectedScopeValue))
        {
            typeValueClass = DETAILS_CLASS_MISSING_VALUE
        }
        assert nodeDetails.contains("""<input id="${scopeKey}" value="${selectedScopeValue}" placeholder="${placeHolder}" class="scopeInput form-control ${typeValueClass} ${topNodeClass}""")
        if (!availableScopeValues && !unavailableScopeValues)
        {
            assert !nodeDetails.contains("""<li id=""")
            return
        }

        availableScopeValues.each{String scopeValue ->
            assert nodeDetails.contains("""<li id="${scopeKey}: ${scopeValue}" class="scopeClick ${topNodeClass}""")
        }
        unavailableScopeValues.each{String scopeValue ->
            assert !nodeDetails.contains("""<li id="${scopeKey}: ${scopeValue}" class="scopeClick ${topNodeClass}""")
        }
    }

    private static void checkNoScopePrompt(Map node, String scopeKey = '')
    {
        String nodeDetails = node.details as String
        assert !nodeDetails.contains("""title="${scopeKey}""")
        assert !nodeDetails.contains("""<input id="${scopeKey}""")
        assert !nodeDetails.contains("""<li id="${scopeKey}""")
    }

    private checkValidRpmClass( String startCubeName)
    {
        assert nodes.size() == 1
        assert edges.size() == 0
        Map node = nodes.values().find { startCubeName == (it as Map).cubeName}
        assert 'ValidRpmClass' == node.title
        assert 'ValidRpmClass' == node.detailsTitle1
        assert null == node.detailsTitle2
        assert 'ValidRpmClass' == node.label
        assert  null == node.typesToAdd
        assert UNSPECIFIED == node.group
        assert null == node.fromFieldName
        assert '1' ==  node.level
        //TODO: assert scopeInfo.scope == node.scope
        String nodeDetails = node.details as String
        assert nodeDetails.contains(DETAILS_LABEL_UTILIZED_SCOPE_WITHOUT_TRAITS)
        assert nodeDetails.contains("${DETAILS_LABEL_FIELDS}<pre><ul></ul></pre>")
        assert !nodeDetails.contains(DETAILS_LABEL_FIELDS_AND_TRAITS)
        assert !nodeDetails.contains(DETAILS_LABEL_CLASS_TRAITS)
    }

    private NCube createNCubeWithValidRpmClass(String cubeName)
    {
        NCube cube = new NCube(cubeName)
        cube.applicationID = appId
        String axisName = AXIS_FIELD
        cube.addAxis(new Axis(axisName, AxisType.DISCRETE, AxisValueType.STRING, false, Axis.SORTED, 1))
        cube.addColumn(axisName, CLASS_TRAITS)
        axisName = AXIS_TRAIT
        cube.addAxis(new Axis(axisName, AxisType.DISCRETE, AxisValueType.STRING, false, Axis.SORTED, 2))
        cube.addColumn(axisName, R_EXISTS)
        cube.addColumn(axisName, R_RPM_TYPE)
        NCubeManager.addCube(cube.applicationID, cube)
        return cube
    }

    private void checkTCoverageGraphScopePrompt()
    {
        //TODO:
        /* Set<String> scopeKeys = ['policyControlDate', 'quoteDate', '_effectiveVersion', 'coverage'] as CaseInsensitiveSet
        assert 4 == scopeInfo.availableScopeValues.keySet().size()
        assert scopeInfo.scopeCubeNames.keySet().containsAll(scopeKeys)
        assert 0 == scopeInfo.optionalGraphScopeAvailableValues.keySet().size()*/
    }

}
