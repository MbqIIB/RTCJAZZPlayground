package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.listcommand.ListFlowTargetsCmd;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil.ItemInfo;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsFlowTargetChange;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPutWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceFlowEntryDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.osgi.util.NLS;





public class FlowTargetCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public FlowTargetCmd() {}
  
  IFilesystemRestClient client = null;
  
  public static final PositionalOptionDefinition OPT_TARGET = new PositionalOptionDefinition("target-workspace", 1, 1);
  public static final NamedOptionDefinition OPT_DEFAULT = new NamedOptionDefinition(null, "default", 0, 1, null);
  public static final NamedOptionDefinition OPT_CURRENT = new NamedOptionDefinition(null, "current", 0, 1, null);
  public static final NamedOptionDefinition OPT_FLOW_COMPS = new NamedOptionDefinition("C", "flow-components", -1);
  public static final NamedOptionDefinition OPT_FLOW_ALL_COMPS = new NamedOptionDefinition("a", "flow-all-components", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@"), Messages.FlowTargetCmdOptions_SOURCEWS_HELP)
      .addOption(OPT_TARGET, Messages.FlowTargetCmdOptions_TARGETWS_HELP)
      .addOption(OPT_DEFAULT, Messages.FlowTargetCmdOptions_DEFAULT_HELP)
      .addOption(OPT_CURRENT, Messages.FlowTargetCmdOptions_CURRENT_HELP)
      .addOption(OPT_FLOW_ALL_COMPS, Messages.FlowTargetCmdOptions_FLOW_ALL_COMPONENTS_HELP)
      .addOption(OPT_FLOW_COMPS, Messages.FlowTargetCmdOptions_FLOW_COMPONENTS_HELP)
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT);
    
    return options;
  }
  



  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    if ((cli.hasOption(OPT_FLOW_ALL_COMPS)) && (cli.hasOption(OPT_FLOW_COMPS))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_2_ARGUMENTS, OPT_FLOW_ALL_COMPS.getName(), OPT_FLOW_COMPS.getName()));
    }
    
    if (cli.hasOption(OPT_DEFAULT)) {
      String value = cli.getOption(OPT_DEFAULT, "b");
      if ((!value.equalsIgnoreCase("i")) && (!value.equalsIgnoreCase("o")) && (!value.equalsIgnoreCase("b"))) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.FlowtargetCmd_INVALID_OPTION_VALUE, OPT_DEFAULT.getName()));
      }
    }
    if (cli.hasOption(OPT_CURRENT)) {
      String value = cli.getOption(OPT_CURRENT, "b");
      if ((!value.equalsIgnoreCase("i")) && (!value.equalsIgnoreCase("o")) && (!value.equalsIgnoreCase("b"))) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.FlowtargetCmd_INVALID_OPTION_VALUE, OPT_CURRENT.getName()));
      }
    }
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    client = SubcommandUtil.setupDaemon(config);
    

    IScmCommandLineArgument srcSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(srcSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, srcSelector);
    IWorkspace wsFound = RepoUtil.getWorkspace(srcSelector.getItemSelector(), true, true, repo, config);
    ParmsWorkspace ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    

    IScmCommandLineArgument targetSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_TARGET), config);
    SubcommandUtil.validateArgument(targetSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    IUuidAliasRegistry.IUuidAlias targetAlias = targetSelector.getAlias();
    

    WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
    Map<String, List<String>> flowNameToItemId = new HashMap();
    Map<String, FlowInfo> flowItemIdToInfo = new HashMap();
    
    List<WorkspaceFlowEntryDTO> wsFlowList = wsDetails.getFlowEntries();
    FlowInfo flowInfo; for (WorkspaceFlowEntryDTO wsFlow : wsFlowList) {
      ITeamRepository flowRepo = null;
      try
      {
        flowRepo = RepoUtil.getSharedRepository(wsFlow.getRepositoryURL(), true);
      }
      catch (IllegalArgumentException localIllegalArgumentException) {}
      


      if (flowRepo == null) {
        try {
          flowRepo = RepoUtil.login(config, client, config.getConnectionInfo(wsFlow.getRepositoryURL(), null, false, true, false));
        }
        catch (Exception localException1) {}
      }
      

      flowInfo = new FlowInfo();
      entryDTO = wsFlow;
      
      if (flowRepo != null) {
        IWorkspace flowWs = null;
        try
        {
          flowWs = RepoUtil.getWorkspace(wsFlow.getWorkspaceItemId(), true, true, flowRepo, config);
          ws = flowWs;
          
          List<String> flowList = (List)flowNameToItemId.get(flowWs.getName());
          if (flowList == null) {
            flowList = new ArrayList();
            flowNameToItemId.put(flowWs.getName(), flowList);
          }
          flowList.add(wsFlow.getWorkspaceItemId());
        }
        catch (Exception localException2) {}
      }
      

      flowItemIdToInfo.put(wsFlow.getWorkspaceItemId(), flowInfo);
    }
    

    if ((targetAlias != null) && (flowItemIdToInfo.keySet().contains(targetAlias.getUuid().getUuidValue()))) {
      peformOperation(ws, (FlowInfo)flowItemIdToInfo.get(targetAlias.getUuid().getUuidValue()), cli);
      return;
    }
    

    List<String> flowList = (List)flowNameToItemId.get(targetSelector.getItemSelector());
    if (flowList != null) {
      if (flowList.size() == 1) {
        peformOperation(ws, (FlowInfo)flowItemIdToInfo.get(flowList.get(0)), cli);
      }
      else {
        Object flowsMatched = new ArrayList(flowList.size());
        for (String flowItemId : flowList) {
          FlowInfo flowInfo = (FlowInfo)flowItemIdToInfo.get(flowItemId);
          ((List)flowsMatched).add(new SubcommandUtil.ItemInfo(targetSelector.getItemSelector(), entryDTO.getWorkspaceItemId(), entryDTO.getRepositoryURL(), 
            ws.isStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE));
        }
        SubcommandUtil.displayAmbiguousSelectorException(targetSelector.getItemSelector(), (List)flowsMatched, config);
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.RemoveFlowTargetsCmd_AMBIGUOUS_FLOWTARGET, targetSelector.getItemSelector()));
      }
    } else {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.FlowTargetCmd_FLOW_TARGET_NOT_FOUND, targetSelector.getItemSelector()));
    }
  }
  
  private void peformOperation(ParmsWorkspace ws, FlowInfo flowInfo, ICommandLine cli) throws FileSystemException
  {
    boolean update = false;
    if ((cli.hasOption(OPT_CURRENT)) || (cli.hasOption(OPT_DEFAULT)) || (cli.hasOption(OPT_FLOW_ALL_COMPS)) || (cli.hasOption(OPT_FLOW_COMPS))) {
      update = true;
    }
    
    if (update) {
      if ((ws == null) && (cli.hasOption(OPT_FLOW_COMPS))) {
        throw StatusHelper.disallowed(Messages.FlowTargetCmd_TARGET_NOT_LOGGED_IN);
      }
      
      List<String> compIds = null;
      if (cli.hasOption(OPT_FLOW_COMPS)) {
        List<IScmCommandLineArgument> compSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_FLOW_COMPS), config);
        SubcommandUtil.validateArgument(compSelectors, RepoUtil.ItemType.COMPONENT);
        compIds = getComponentItemIds(entryDTO, compSelectors);
      }
      
      String defaultValue = cli.hasOption(OPT_DEFAULT) ? cli.getOption(OPT_DEFAULT, "b") : null;
      boolean defaultIncoming = defaultValue != null;
      boolean defaultOutgoing = defaultValue != null;
      String currentValue = cli.hasOption(OPT_CURRENT) ? cli.getOption(OPT_CURRENT, "b") : null;
      boolean currentIncoming = currentValue != null;
      boolean currentOutgoing = currentValue != null;
      
      updateFlowTarget(ws, entryDTO, defaultIncoming, defaultOutgoing, currentIncoming, currentOutgoing, cli.hasOption(OPT_FLOW_ALL_COMPS), compIds);
    } else {
      JSONObject jFlowTarget = getFlowTargetInfo(entryDTO, ws);
      printFlowTarget(jFlowTarget);
    }
  }
  
  private List<String> getComponentItemIds(WorkspaceFlowEntryDTO wsFlow, List<IScmCommandLineArgument> compSelectors) throws FileSystemException {
    List<String> compIds = new ArrayList();
    
    ParmsWorkspace parmsFlowWs = new ParmsWorkspace(wsFlow.getRepositoryURL(), wsFlow.getWorkspaceItemId());
    WorkspaceDetailsDTO flowWsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(parmsFlowWs), client, config).get(0);
    
    for (IScmCommandLineArgument compSelector : compSelectors) {
      IUuidAliasRegistry.IUuidAlias compAlias = RepoUtil.lookupUuidAndAlias(compSelector.getItemSelector());
      

      List<WorkspaceComponentDTO> compsFound = new ArrayList();
      for (WorkspaceComponentDTO compDTO : flowWsDetails.getComponents()) {
        if (((compAlias != null) && (compAlias.getUuid().getUuidValue().equals(compDTO.getItemId()))) || 
          (compSelector.getItemSelector().equals(compDTO.getName()))) {
          compsFound.add(compDTO);
        }
      }
      
      if (compsFound.isEmpty())
        throw StatusHelper.itemNotFound(NLS.bind(Messages.FlowTargetCmd_COMP_NOT_FOUND_IN_WS, compSelector.getItemSelector(), 
          AliasUtil.selector(flowWsDetails.getName(), UUID.valueOf(flowWsDetails.getItemId()), flowWsDetails.getRepositoryURL(), 
          flowWsDetails.isStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE)));
      if (compsFound.size() > 1)
      {
        List<SubcommandUtil.ItemInfo> compsMatched = new ArrayList(compsFound.size());
        for (WorkspaceComponentDTO comp : compsFound) {
          compsMatched.add(new SubcommandUtil.ItemInfo(comp.getName(), comp.getItemId(), wsFlow.getRepositoryURL(), RepoUtil.ItemType.COMPONENT));
        }
        SubcommandUtil.displayAmbiguousSelectorException(compSelector.getItemSelector(), compsMatched, config);
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.Common_AMBIGUOUS_COMPONENT, compSelector.getItemSelector()));
      }
      
      compIds.add(((WorkspaceComponentDTO)compsFound.get(0)).getItemId());
    }
    
    return compIds;
  }
  
  private void updateFlowTarget(ParmsWorkspace ws, WorkspaceFlowEntryDTO wsFlow, boolean setDefaultIncoming, boolean setDefaultOutgoing, boolean setCurrentIncoming, boolean setCurrentOutgoing, boolean allComps, List<String> compIds) throws FileSystemException
  {
    ParmsWorkspace flowWs = new ParmsWorkspace(wsFlow.getRepositoryURL(), wsFlow.getWorkspaceItemId());
    ParmsPutWorkspace parms = new ParmsPutWorkspace();
    workspace = ws;
    
    if (setDefaultIncoming) {
      defaultIncomingFlowTarget = flowWs;
    }
    if (setDefaultOutgoing) {
      defaultOutgoingFlowTarget = flowWs;
    }
    if (setCurrentIncoming) {
      currentIncomingFlowTarget = flowWs;
    }
    if (setCurrentOutgoing) {
      currentOutgoingFlowTarget = flowWs;
    }
    if ((allComps) || (compIds != null)) {
      flowTargets = new ParmsFlowTargetChange[1];
      ParmsFlowTargetChange flowTargetChange = new ParmsFlowTargetChange();
      workspace = flowWs;
      if (compIds != null) {
        scopedComponentItemIds = ((String[])compIds.toArray(new String[compIds.size()]));
      }
      if (allComps) {
        scopedComponentItemIds = new String[0];
      }
      flowTargets[0] = flowTargetChange;
    }
    try
    {
      client.postPutWorkspace(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.FlowTargetsCmd_FAILURE, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
    
    config.getContext().stdout().println(Messages.FlowTargetsCmd_SUCCESS);
  }
  
  private JSONObject getFlowTargetInfo(WorkspaceFlowEntryDTO wsFlow, IWorkspace flowWs) throws FileSystemException {
    JSONObject jFlowTarget = new JSONObject();
    jFlowTarget.put("incoming-flow", wsFlow.isIncomingFlow() ? Boolean.TRUE : Boolean.FALSE);
    jFlowTarget.put("outgoing-flow", wsFlow.isOutgoingFlow() ? Boolean.TRUE : Boolean.FALSE);
    jFlowTarget.put("default-incoming", wsFlow.isDefaultIncomingFlow() ? Boolean.TRUE : Boolean.FALSE);
    jFlowTarget.put("default-outgoing", wsFlow.isDefaultOutgoingFlow() ? Boolean.TRUE : Boolean.FALSE);
    jFlowTarget.put("current-incoming", wsFlow.isCurrentIncomingFlow() ? Boolean.TRUE : Boolean.FALSE);
    jFlowTarget.put("current-outgoing", wsFlow.isCurrentOutgoingFlow() ? Boolean.TRUE : Boolean.FALSE);
    jFlowTarget.put("uuid", wsFlow.getWorkspaceItemId());
    jFlowTarget.put("url", wsFlow.getRepositoryURL());
    jFlowTarget.put("scoped", wsFlow.getScopedComponentItemIds().isEmpty() ? Boolean.FALSE : Boolean.TRUE);
    
    Map<String, String> compIdToName = new HashMap();
    
    if (flowWs != null) {
      jFlowTarget.put("name", flowWs.getName());
      jFlowTarget.put("type", flowWs.isStream() ? RepoUtil.ItemType.STREAM.toString() : RepoUtil.ItemType.WORKSPACE.toString());
      
      new JSONArray();
      
      if (!wsFlow.getScopedComponentItemIds().isEmpty())
      {
        WorkspaceDetailsDTO flowTargetDetails = 
          (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(new ParmsWorkspace(wsFlow.getRepositoryURL(), wsFlow.getWorkspaceItemId())), client, config).get(0);
        

        for (WorkspaceComponentDTO compDTO : flowTargetDetails.getComponents()) {
          compIdToName.put(compDTO.getItemId(), compDTO.getName());
        }
      }
    } else {
      jFlowTarget.put("name", wsFlow.getWorkspaceItemId());
      jFlowTarget.put("type", RepoUtil.ItemType.UNKNOWN.toString());
    }
    

    JSONArray jCompArray = new JSONArray();
    for (String compId : wsFlow.getScopedComponentItemIds()) {
      JSONObject jComp = new JSONObject();
      jComp.put("uuid", compId);
      if (compIdToName.containsKey(compId)) {
        jComp.put("name", compIdToName.get(compId));
      } else {
        jComp.put("name", compId);
      }
      
      jCompArray.add(jComp);
    }
    jFlowTarget.put("components", jCompArray);
    
    return jFlowTarget;
  }
  
  private void printFlowTarget(JSONObject jFlowTarget) {
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jFlowTarget);
      return;
    }
    
    ListFlowTargetsCmd.printFlowTarget(jFlowTarget, out);
  }
  
  class FlowInfo
  {
    public IWorkspace ws;
    public WorkspaceFlowEntryDTO entryDTO;
    
    FlowInfo() {}
  }
}
