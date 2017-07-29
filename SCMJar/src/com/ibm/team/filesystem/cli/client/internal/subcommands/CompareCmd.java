package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.AliasUtil.IAliasOptions;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil.ChangeRequestInfo;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.internal.snapshot.FlowType;
import com.ibm.team.filesystem.client.internal.snapshot.SnapshotId;
import com.ibm.team.filesystem.client.internal.snapshot.SnapshotSyncReport;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.common.changemodel.IPathResolver;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogBaselineEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogChangeSetEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogComponentEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogDirectionEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogOslcLinkEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogVersionableEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogWorkItemEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.BaselineDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ContributorDTO;
import com.ibm.team.filesystem.rcp.core.internal.changelog.BaseChangeLogEntryVisitor;
import com.ibm.team.filesystem.rcp.core.internal.changelog.ChangeLogCustomizer;
import com.ibm.team.filesystem.rcp.core.internal.changelog.ChangeLogStreamOutput;
import com.ibm.team.filesystem.rcp.core.internal.changelog.GenerateChangeLogOperation;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.ComparePathResolver;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.CopyFileAreaPathResolver;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.SnapshotPathResolver;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.List;
import org.eclipse.osgi.util.NLS;















































public class CompareCmd
  extends AbstractSubcommand
{
  public CompareCmd() {}
  
  private static class CLIChangeLogEntryVisitor
    extends BaseChangeLogEntryVisitor
  {
    String repoUri;
    IScmClientConfiguration config = null;
    JSONObject currJsonObj = null;
    
    public CLIChangeLogEntryVisitor(IChangeLogOutput out, String repoUri, IScmClientConfiguration config)
    {
      setOutput(out);
      this.repoUri = repoUri;
      this.config = config;
    }
    
    private void jsonizeDTO(ChangeLogEntryDTO dto, JSONObject obj) {
      obj.put("name", dto.getEntryName());
      obj.put("uuid", dto.getItemId());
      obj.put("url", repoUri);
    }
    
    protected String findComponentName(ChangeLogComponentEntryDTO dto)
    {
      return AliasUtil.selector(dto.getEntryName(), UUID.valueOf(dto.getItemId()), repoUri, RepoUtil.ItemType.COMPONENT);
    }
    
    protected String findChangeSetName(ChangeLogChangeSetEntryDTO dto)
    {
      String alias = AliasUtil.alias(dto.getItemId(), repoUri, RepoUtil.ItemType.CHANGESET);
      if ((alias != null) && (alias.length() > 0)) {
        return NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.CompareCmd_CHANGESET_ALIAS_AND_NAME, alias, super.findChangeSetName(dto));
      }
      
      return super.findChangeSetName(dto);
    }
    
    protected String findBaselineName(ChangeLogBaselineEntryDTO dto)
    {
      String alias = AliasUtil.alias(dto.getItemId(), repoUri, RepoUtil.ItemType.BASELINE);
      if ((alias != null) && (alias.length() > 0)) {
        return NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.CompareCmd_CHANGESET_ALIAS_AND_NAME, alias, super.findBaselineName(dto));
      }
      
      return super.findBaselineName(dto);
    }
    
    protected String findVersionableName(ChangeLogVersionableEntryDTO dto)
    {
      if (config.getAliasConfig().showUuid()) {
        String itemIdStr = NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.Common_SURROUND_PARANTHESIS, dto.getItemId());
        return NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.CompareCmd_CHANGESET_ALIAS_AND_NAME, itemIdStr, super.findVersionableName(dto));
      }
      
      return super.findVersionableName(dto);
    }
    
    protected String findWorkItemName(ChangeLogWorkItemEntryDTO dto)
    {
      String alias = AliasUtil.alias(dto.getItemId(), repoUri, RepoUtil.ItemType.WORKITEM);
      if ((alias != null) && (alias.length() > 0)) {
        return NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.CompareCmd_CHANGESET_ALIAS_AND_NAME, alias, super.findWorkItemName(dto));
      }
      
      return super.findWorkItemName(dto);
    }
    
    public void acceptInto(ChangeLogEntryDTO root, JSONObject jObj) {
      if (!enter(root)) {
        return;
      }
      
      JSONArray wiArray;
      
      if ("clentry_changeset".equals(root.getEntryType())) {
        ChangeLogChangeSetEntryDTO cs = (ChangeLogChangeSetEntryDTO)root;
        
        wiArray = new JSONArray();
        JSONObject wiObj;
        for (ChangeLogWorkItemEntryDTO wi : cs.getWorkItems()) {
          wiObj = createEntry(root, wi);
          if (wiObj.size() > 0) {
            wiArray.add(wiObj);
          }
        }
        
        if (wiArray.size() > 0) {
          jObj.put("workitems", wiArray);
        }
        
        JSONArray oslcArray = new JSONArray();
        
        for (ChangeLogOslcLinkEntryDTO link : cs.getOslcLinks()) {
          JSONObject wiObj = createEntry(root, link);
          if (wiObj.size() > 0) {
            oslcArray.add(wiObj);
          }
        }
        
        if (oslcArray.size() > 0) {
          jObj.put("change-requests", oslcArray);
        }
      }
      
      for (ChangeLogEntryDTO child : root.getChildEntries()) {
        String entryType = getEntryType(child);
        
        JSONArray childArray = (JSONArray)jObj.get(entryType);
        if (childArray == null) {
          childArray = new JSONArray();
          jObj.put(entryType, childArray);
        }
        
        JSONObject childObj = createEntry(root, child);
        if (childObj.size() > 0) {
          childArray.add(childObj);
        }
      }
      
      exit(root);
    }
    
    public JSONObject createEntry(ChangeLogEntryDTO root, ChangeLogEntryDTO child) {
      JSONObject jobj = new JSONObject();
      currJsonObj = jobj;
      
      visitChild(root, child);
      
      JSONArray arr = new JSONArray();
      String entryType = getEntryType(child);
      jobj.put(entryType, arr);
      
      acceptInto(child, jobj);
      
      if (arr.size() == 0) {
        jobj.remove(entryType);
      }
      
      return jobj;
    }
    
    protected void visitDirection(ChangeLogEntryDTO parent, ChangeLogDirectionEntryDTO dto)
    {
      if (!config.isJSONEnabled()) {
        super.visitDirection(parent, dto);
        return;
      }
      
      if (getShowDirectionNodes()) {
        boolean incoming = true;
        boolean outgoing = false;
        if (!"incoming".equals(dto.getFlowDirection())) {
          incoming = false;
          outgoing = true;
        }
        currJsonObj.put("incoming-changes", Boolean.valueOf(incoming));
        currJsonObj.put("outgoing-changes", Boolean.valueOf(outgoing));
      }
    }
    
    protected void visitComponent(ChangeLogEntryDTO parent, ChangeLogComponentEntryDTO dto)
    {
      if (!config.isJSONEnabled()) {
        super.visitComponent(parent, dto);
        return;
      }
      
      currJsonObj.put("item-type", "component");
      jsonizeDTO(dto, currJsonObj);
      

      if ("addComponent".equals(dto.getChangeType())) {
        currJsonObj.put("added", Boolean.valueOf(true));
        currJsonObj.put("removed", Boolean.valueOf(false));
      }
      else if ("removeComponent".equals(dto.getChangeType())) {
        currJsonObj.put("removed", Boolean.valueOf(true));
        currJsonObj.put("added", Boolean.valueOf(false));
      } else {
        currJsonObj.put("added", Boolean.valueOf(false));
        currJsonObj.put("removed", Boolean.valueOf(false));
      }
    }
    
    protected void visitChangeSet(ChangeLogEntryDTO parent, ChangeLogChangeSetEntryDTO dto)
    {
      if (!config.isJSONEnabled()) {
        super.visitChangeSet(parent, dto);
        return;
      }
      
      currJsonObj.put("item-type", "changeset");
      if (dto.getEntryName() != null) {
        currJsonObj.put("comment", dto.getEntryName());
      }
      currJsonObj.put("url", repoUri);
      currJsonObj.put("uuid", dto.getItemId());
      
      if (dto.getWorkItems().size() > 0) {
        JSONArray wiArray = new JSONArray();
        
        for (ChangeLogWorkItemEntryDTO wi : dto.getWorkItems()) {
          JSONObject wiObj = new JSONObject();
          wiObj.put("item-type", "workitem");
          wiObj.put("uuid", wi.getItemId());
          wiObj.put("url", repoUri);
          wiObj.put("workitem-number", Long.valueOf(wi.getWorkItemNumber()));
          wiObj.put("workitem-label", wi.getEntryName());
          
          wiArray.add(wiObj);
        }
        
        if (wiArray.size() > 0) {
          currJsonObj.put("workitems", wiArray);
        }
      }
      if (dto.getOslcLinks().size() > 0) {
        JSONArray oslcLinksArray = new JSONArray();
        for (ChangeLogOslcLinkEntryDTO oL : dto.getOslcLinks()) {
          JSONObject olObj = new JSONObject();
          String oslcLabel = oL.getEntryName();
          oslcLabel = SubcommandUtil.sanitizeHTMLText(oslcLabel);
          
          JSONPrintUtil.ChangeRequestInfo oslcInfo = new JSONPrintUtil.ChangeRequestInfo(oslcLabel);
          
          olObj.put("info", oslcInfo.getDisplayString(true));
          
          String targetUri = oL.getTargetUri();
          
          if (targetUri == null) {
            olObj.put("target-uri", com.ibm.team.filesystem.cli.core.internal.Messages.PendingChangesUtil_NO_TARGET_URI);
          } else {
            olObj.put("target-uri", targetUri);
          }
          oslcLinksArray.add(olObj);
        }
        
        if (oslcLinksArray.size() > 0) {
          currJsonObj.put("change-requests", oslcLinksArray);
        }
      }
      
      if ((getShowChangeSetCreator()) && (dto.getCreator() != null)) {
        JSONObject creatorObj = new JSONObject();
        creatorObj.put("userId", dto.getCreator().getUserId());
        creatorObj.put("userName", dto.getCreator().getFullName());
        creatorObj.put("mail", dto.getCreator().getEmailAddress());
        creatorObj.put("uuid", dto.getCreator().getContributorItemId());
        
        currJsonObj.put("author", creatorObj);
      }
      
      if ((getShowChangeSetCreationDate()) && (dto.getCreationDate() != 0L)) {
        SimpleDateFormat formatter = new SimpleDateFormat(getDateFormat());
        String date = formatter.format(Long.valueOf(dto.getCreationDate()));
        currJsonObj.put("creationDate", date);
      }
    }
    
    protected void visitBaseline(ChangeLogEntryDTO parent, ChangeLogBaselineEntryDTO dto)
    {
      if (!config.isJSONEnabled()) {
        super.visitBaseline(parent, dto);
        return;
      }
      
      currJsonObj.put("item-type", "baseline");
      jsonizeDTO(dto, currJsonObj);
      currJsonObj.put("id", Integer.valueOf(dto.getBaselineId()));
    }
    
    protected void visitVersionable(ChangeLogEntryDTO parent, ChangeLogVersionableEntryDTO dto)
    {
      if (!config.isJSONEnabled()) {
        super.visitVersionable(parent, dto);
        return;
      }
      
      currJsonObj.put("item-type", "versionable");
      currJsonObj.put("uuid", dto.getItemId());
      currJsonObj.put("url", repoUri);
      currJsonObj.put("path", dto.getEntryName());
    }
    
    protected void visitWorkItem(ChangeLogEntryDTO parent, ChangeLogWorkItemEntryDTO dto, boolean inChangeSet)
    {
      if (!config.isJSONEnabled()) {
        super.visitWorkItem(parent, dto, inChangeSet);
        return;
      }
      
      if ((!inChangeSet) || (getShowChangeSetWorkItems())) {
        currJsonObj.put("item-type", "workitem");
        currJsonObj.put("uuid", dto.getItemId());
        currJsonObj.put("url", repoUri);
        currJsonObj.put("workitem-number", Long.valueOf(dto.getWorkItemNumber()));
        currJsonObj.put("workitem-label", dto.getEntryName());
      }
    }
    
    protected void visitOslcLink(ChangeLogEntryDTO parent, ChangeLogOslcLinkEntryDTO dto)
    {
      if (!config.isJSONEnabled()) {
        super.visitOslcLink(parent, dto);
        return;
      }
      
      if (getShowChangeSetOslcLink()) {
        currJsonObj.put("item-type", "change-requests");
        String oslcLabel = dto.getEntryName();
        oslcLabel = SubcommandUtil.sanitizeHTMLText(oslcLabel);
        JSONPrintUtil.ChangeRequestInfo oslcInfo = new JSONPrintUtil.ChangeRequestInfo(oslcLabel);
        currJsonObj.put("info", oslcInfo.getDisplayString(true));
        String targetUri = dto.getTargetUri();
        if (targetUri == null) {
          currJsonObj.put("target-uri", com.ibm.team.filesystem.cli.core.internal.Messages.PendingChangesUtil_NO_TARGET_URI);
        } else {
          currJsonObj.put("target-uri", targetUri);
        }
      }
    }
    
    public static String getEntryType(ChangeLogEntryDTO entry) {
      String entryType = entry.getEntryType();
      if ("clentry_direction".equals(entryType)) {
        return "direction";
      }
      if ("clentry_component".equals(entryType)) {
        return "components";
      }
      if ("clentry_changeset".equals(entryType)) {
        return "changesets";
      }
      if ("clentry_baseline".equals(entryType)) {
        return "baselines";
      }
      if ("clentry_versionable".equals(entryType)) {
        return "versionables";
      }
      if ("clentry_workitem".equals(entryType)) {
        return "workitems";
      }
      if ("clentry_oslc".equals(entryType)) {
        return "change-requests";
      }
      
      return "entries";
    }
  }
  
  static enum CompareType
  {
    WORKSPACE("workspace", new String[] { "workspace", "ws" }), 
    STREAM("workspace", new String[] { "stream", "s" }), 
    BASELINE("baseline", new String[] { "baseline", "bl" }), 
    SNAPSHOT("baselineset", new String[] { "snapshot", "ss" });
    
    static final String[] NAMES = { WORKSPACEtypeStrings[0], STREAMtypeStrings[0], BASELINEtypeStrings[0], SNAPSHOTtypeStrings[0] };
    final String[] typeStrings;
    final String wireName;
    
    private CompareType(String wireName, String... t)
    {
      this.wireName = wireName;
      typeStrings = t;
    }
    
    String[] getNames() {
      return typeStrings;
    }
    
    String getWireName() {
      return wireName;
    }
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine subargs = config.getSubcommandCommandLine();
    
    config.setEnableJSON(subargs.hasOption(CommonOptions.OPT_JSON));
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    IScmCommandLineArgument repoSelector = null;
    



    if (subargs.getOption(CompareCmdOpts.OPT_COMPARE_TYPE_2).equals(CompareType.STREAM)) {
      repoSelector = ScmCommandLineArgument.create(subargs.getOptionValue(CompareCmdOpts.OPT_COMPARE_ITEM_2), config);

    }
    else if (subargs.getOption(CompareCmdOpts.OPT_COMPARE_TYPE_1).equals(CompareType.STREAM)) {
      repoSelector = ScmCommandLineArgument.create(subargs.getOptionValue(CompareCmdOpts.OPT_COMPARE_ITEM_1), config);

    }
    else if (subargs.getOption(CompareCmdOpts.OPT_COMPARE_TYPE_1).equals(CompareType.WORKSPACE)) {
      repoSelector = ScmCommandLineArgument.create(subargs.getOptionValue(CompareCmdOpts.OPT_COMPARE_ITEM_1), config);
    }
    else
    {
      repoSelector = ScmCommandLineArgument.create(subargs.getOptionValue(CompareCmdOpts.OPT_COMPARE_ITEM_1), config);
    }
    
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, repoSelector);
    

    IScmCommandLineArgument componentSelector = ScmCommandLineArgument.create(subargs.getOptionValue(CompareCmdOpts.OPT_COMPONENT, null), config);
    SubcommandUtil.validateArgument(componentSelector, RepoUtil.ItemType.COMPONENT);
    
    CompareType type1 = findType(subargs.getOption(CompareCmdOpts.OPT_COMPARE_TYPE_1));
    IItemHandle item1 = resolveItem(type1, ScmCommandLineArgument.create(subargs.getOptionValue(CompareCmdOpts.OPT_COMPARE_ITEM_1), config), 
      componentSelector != null ? componentSelector.getItemSelector() : null, client, config);
    
    CompareType type2 = findType(subargs.getOption(CompareCmdOpts.OPT_COMPARE_TYPE_2));
    IItemHandle item2 = resolveItem(type2, ScmCommandLineArgument.create(subargs.getOptionValue(CompareCmdOpts.OPT_COMPARE_ITEM_2), config), 
      componentSelector != null ? componentSelector.getItemSelector() : null, client, config);
    

    SnapshotId snapshotId1 = SnapshotId.getSnapshotId(item1);
    SnapshotId snapshotId2 = SnapshotId.getSnapshotId(item2);
    
    try
    {
      stefansSyncReport = SnapshotSyncReport.compare(snapshotId1.getSnapshot(null), snapshotId2.getSnapshot(null), null, null);
    } catch (TeamRepositoryException e) { SnapshotSyncReport stefansSyncReport;
      throw StatusHelper.wrap("Compare failed", e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    
    SnapshotSyncReport stefansSyncReport;
    
    GenerateChangeLogOperation clOp = new GenerateChangeLogOperation();
    
    ChangeLogCustomizer customizer = createChangeLogParms2(subargs, config);
    
    IPathResolver pathResolver = new ComparePathResolver(false, 
      CopyFileAreaPathResolver.create(), 
      SnapshotPathResolver.create(snapshotId1), 
      SnapshotPathResolver.create(snapshotId2));
    
    clOp.setChangeLogRequest(
      repo, stefansSyncReport, 
      pathResolver, 
      customizer);
    
    try
    {
      changelog = clOp.run(null);
    } catch (TeamRepositoryException e) { ChangeLogEntryDTO changelog;
      throw StatusHelper.wrap("Failed to generate change log", e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    ChangeLogEntryDTO changelog;
    CLIChangeLogEntryVisitor visitor = new CLIChangeLogEntryVisitor(
      new ChangeLogStreamOutput(config.getContext().stdout()), 
      repo.getRepositoryURI(), 
      config);
    visitor.setContributorFormat(subargs.getOption(CompareCmdOpts.OPT_FMT_CONTRIB, visitor.getContribFormat()));
    
    SimpleDateFormat sdf = SubcommandUtil.getDateFormat(visitor.getDateFormat(), config);
    visitor.setDateFormat(subargs.getOption(CompareCmdOpts.OPT_FMT_DATE, sdf.toPattern()));
    
    if (!config.isJSONEnabled())
    {
      boolean shouldShowContrib = findShouldShowContrib(subargs);
      boolean shouldShowDate = findShouldShowDate(subargs);
      boolean shouldShowChangeSetWorkItems = findShouldShowChangeSetWorkItems(subargs);
      boolean shouldShowFlowDirection = findShouldShowFlowDirection(subargs);
      boolean shouldShowChangeSetOslcLinks = findShouldShowChangeSetOslcLinks(subargs);
      
      visitor.setShowBaselineCreationDate(shouldShowDate);
      visitor.setShowChangeSetCreationDate(shouldShowDate);
      
      visitor.setShowBaselineCreator(shouldShowContrib);
      visitor.setShowChangeSetCreator(shouldShowContrib);
      
      visitor.setShowChangeSetWorkItems(shouldShowChangeSetWorkItems);
      visitor.setShowDirectionNodes(shouldShowFlowDirection);
      visitor.setShowChangeSetOslcLink(shouldShowChangeSetOslcLinks);
    }
    
    JSONObject obj = new JSONObject();
    visitor.acceptInto(changelog, obj);
    
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(obj);
    }
  }
  
  private ChangeLogCustomizer createChangeLogParms2(ICommandLine subargs, IScmClientConfiguration config) throws FileSystemException
  {
    ChangeLogCustomizer customizer = new ChangeLogCustomizer();
    
    String show = subargs.getOption(CompareCmdOpts.OPT_SHOW, "dcbswo");
    
    if ((show.contains("A")) || (config.isJSONEnabled())) {
      customizer.setIncludeDirection(true);
      customizer.setIncludeBaselines(true);
      customizer.setIncludeChangeSets(true);
      customizer.setIncludeComponents(true);
      customizer.setIncludePaths(true);
      customizer.setIncludeOslcLinks(true);
    } else {
      SwitchString ss = new SwitchString(show);
      
      customizer.setIncludeDirection(ss.contains("d"));
      customizer.setIncludeBaselines(ss.contains("b"));
      customizer.setIncludeChangeSets(ss.contains("s"));
      customizer.setIncludeComponents(ss.contains("c"));
      customizer.setIncludePaths(ss.contains("f"));
      customizer.setIncludeWorkItems(ss.contains("w"));
      customizer.setIncludeOslcLinks(ss.contains("o"));
      
      if (ss.getRemainder().length() > 0) {
        throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.CompareCmd_BAD_INCLUDE_SWITCHES, ss.getRemainder()));
      }
    }
    

    String dir = subargs.getOption(CompareCmdOpts.OPT_DIRECTIONS, null);
    if ((dir == null) || ("b".equals(dir))) {
      customizer.setFlowsToInclude(FlowType.Both);
    }
    else if ("i".equals(dir)) {
      customizer.setFlowsToInclude(FlowType.Incoming);
    }
    else if ("o".equals(dir)) {
      customizer.setFlowsToInclude(FlowType.Outgoing);
    }
    else {
      throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.CompareCmd_BAD_FLOW_DIRECTION, 
        new String[] { dir, "b", "i", "o" }));
    }
    

    String reroot = subargs.getOption(CompareCmdOpts.OPT_REROOT, null);
    if (reroot != null) {
      reroot = reroot.trim();
      
      if ("r".equals(reroot)) {
        customizer.setWorkItemRerootDepth("clentry_root");
      }
      else if ("d".equals(reroot)) {
        customizer.setWorkItemRerootDepth("clentry_direction");
      }
      else if ("c".equals(reroot)) {
        customizer.setWorkItemRerootDepth("clentry_component");
      }
      else if ("b".equals(reroot)) {
        customizer.setWorkItemRerootDepth("clentry_baseline");
      }
      else {
        throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.CompareCmd_UNKNOWN_REROOT_TYPE, new String[] { reroot, "r", "d", "c", "b" }));
      }
    }
    

    String filter = subargs.getOption(CompareCmdOpts.OPT_PRUNE, null);
    if (filter != null) {
      SwitchString ss = new SwitchString(filter);
      
      customizer.setPruneEmptyDirections(ss.contains("d"));
      customizer.setPruneUnchangedComponents(ss.contains("c"));
      
      if (ss.getRemainder().length() > 0) {
        throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.CompareCmd_BAD_PRUNE_SWITCHES, ss.getRemainder()));
      }
    }
    
    return customizer;
  }
  
  private boolean findShouldShowFlowDirection(ICommandLine subargs) {
    String show = subargs.getOption(CompareCmdOpts.OPT_SHOW, "dcbswo");
    return (show.contains("d")) || ("A".equals(show));
  }
  
  private boolean findShouldShowContrib(ICommandLine subargs) {
    String display = subargs.getOption(CompareCmdOpts.OPT_DISPLAY, "cdo");
    return display.contains("c");
  }
  
  private boolean findShouldShowDate(ICommandLine subargs) {
    String display = subargs.getOption(CompareCmdOpts.OPT_DISPLAY, "cdo");
    return display.contains("d");
  }
  
  private boolean findShouldShowChangeSetWorkItems(ICommandLine subargs) {
    String display = subargs.getOption(CompareCmdOpts.OPT_DISPLAY, "cdo");
    return display.contains("i");
  }
  
  private boolean findShouldShowChangeSetOslcLinks(ICommandLine subargs) {
    String display = subargs.getOption(CompareCmdOpts.OPT_DISPLAY, "cdo");
    return display.contains("o");
  }
  
  private static class SwitchString {
    String switches;
    
    SwitchString(String switches) {
      this.switches = switches;
    }
    
    public boolean contains(String s) {
      if (switches.contains(s)) {
        switches = switches.replace(s, "");
        return true;
      }
      
      return false;
    }
    
    public String getRemainder() {
      return switches;
    }
  }
  





  private IItemHandle resolveItem(CompareType type, IScmCommandLineArgument selector, String componentSelector, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ITeamRepository repo1 = RepoUtil.loginUrlArgAncestor(config, client, selector);
    IItemHandle itemHandle = null;
    
    switch (type) {
    case BASELINE: 
      SubcommandUtil.validateArgument(selector, RepoUtil.ItemType.WORKSPACE);
      IWorkspace ws = RepoUtil.getWorkspace(selector.getItemSelector(), true, false, repo1, config);
      itemHandle = ws.getItemHandle();
      break;
    
    case SNAPSHOT: 
      SubcommandUtil.validateArgument(selector, RepoUtil.ItemType.STREAM);
      IWorkspace stream = RepoUtil.getWorkspace(selector.getItemSelector(), false, true, repo1, config);
      itemHandle = stream.getItemHandle();
      break;
    
    case STREAM: 
      SubcommandUtil.validateArgument(selector, RepoUtil.ItemType.BASELINE);
      
      if (componentSelector == null) {
        IUuidAliasRegistry.IUuidAlias blAlias = RepoUtil.lookupUuidAndAlias(selector.getItemSelector(), repo1.getRepositoryURI());
        if (blAlias != null) {
          BaselineDTO blDTO = RepoUtil.getBaselineById(blAlias.getUuid().getUuidValue(), 
            repo1.getRepositoryURI(), client, config);
          if (blDTO == null) {
            throw StatusHelper.itemNotFound(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.CompareCmd_UNMATCHED_BASELINE_OR_COMP_MISSING, 
              selector.getItemSelector(), CompareCmdOpts.OPT_COMPONENT.getLongOpt()));
          }
          itemHandle = (IBaseline)RepoUtil.getItem(IBaseline.ITEM_TYPE, UUID.valueOf(blDTO.getItemId()), 
            repo1, config);
        } else {
          throw StatusHelper.argSyntax(com.ibm.team.filesystem.cli.client.internal.Messages.CompareCmd_SPECIFY_COMPONENT);
        }
      } else {
        IComponent comp = RepoUtil.getComponent(componentSelector, repo1, config);
        
        IBaseline bl = RepoUtil.getBaseline(selector.getItemSelector(), comp.getItemId().getUuidValue(), 
          comp.getName(), repo1, client, config);
        itemHandle = bl.getItemHandle();
      }
      break;
    
    case WORKSPACE: 
      SubcommandUtil.validateArgument(selector, RepoUtil.ItemType.SNAPSHOT);
      itemHandle = RepoUtil.getSnapshot(null, selector.getItemSelector(), repo1, config);
    }
    
    
    return itemHandle;
  }
  
  private CompareType findType(String typeString) throws FileSystemException {
    for (CompareType t : ) {
      for (String candidate : t.getNames()) {
        if (candidate.equals(typeString)) {
          return t;
        }
      }
    }
    

    throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.CompareCmd_UNKNOWN_TYPE_STRING, new String[] { typeString, CompareType.NAMES[0], CompareType.NAMES[1], CompareType.NAMES[2], CompareType.NAMES[3] }));
  }
  
  void cliVisitor(JSONObject obj, ChangeLogCustomizer customizer, ChangeLogStreamOutput out)
  {
    int indent = 0;
    
    if (obj.get("entries") != null) {
      JSONArray rootEntries = (JSONArray)obj.get("entries");
      out.setIndent(indent);
      
      JSONArray incoming = getEntry("direction", "Incoming Changes", rootEntries);
      JSONArray outgoing = getEntry("direction", "Outgoing Changes", rootEntries);
      
      if ((outgoing != null) && (customizer.shouldIncludeOutgoing())) {
        out.writeLine("Outgoing Changes");
        processEntries(outgoing, out, indent);
        if ((incoming == null) && (!customizer.shouldPruneEmptyDirections()) && (customizer.shouldIncludeIncoming())) {
          out.writeLine("Incoming Changes");
        }
      }
      
      if ((incoming != null) && (customizer.shouldIncludeIncoming())) {
        if ((outgoing == null) && (!customizer.shouldPruneEmptyDirections()) && (customizer.shouldIncludeOutgoing())) {
          out.writeLine("Outgoing Changes");
        }
        out.writeLine("Incoming Changes");
        processEntries(incoming, out, indent);
      }
      
      if ((incoming == null) && (outgoing == null)) {
        processEntries(rootEntries, out, indent);
      }
    }
  }
  
  void processEntries(JSONArray entries, ChangeLogStreamOutput out, int indent)
  {
    if (entries == null) {
      return;
    }
    
    indent++;
    
    for (Object obj : entries)
    {
      JSONObject jObj = (JSONObject)obj;
      
      out.setIndent(indent);
      out.writeLine((String)jObj.get("data"));
      
      JSONArray childEntries = (JSONArray)jObj.get("entries");
      
      processEntries(childEntries, out, indent + 1);
    }
  }
  
  JSONArray getEntry(String type, String value, JSONArray arr)
  {
    if (arr == null) {
      return null;
    }
    
    for (Object obj : arr)
    {
      JSONObject jObj = (JSONObject)obj;
      
      if (jObj.get(type) != null)
      {
        String enValue = (String)jObj.get(type);
        
        if (enValue.equals(value)) {
          return (JSONArray)jObj.get("entries");
        }
      }
    }
    
    return null;
  }
}
