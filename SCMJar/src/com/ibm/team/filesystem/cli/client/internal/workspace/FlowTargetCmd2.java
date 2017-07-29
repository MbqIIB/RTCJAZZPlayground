package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.subcommands.FlowComponentTargetCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.FlowWorkspaceTargetCmd;
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
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
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






public class FlowTargetCmd2
  extends AbstractSubcommand
  implements IOptionSource
{
  public FlowTargetCmd2() {}
  
  IFilesystemRestClient client = null;
  
  public static final PositionalOptionDefinition OPT_TARGET = new PositionalOptionDefinition("target-workspace", 0, 1, "@");
  public static final NamedOptionDefinition OPT_FLOW_DIRECTION = new NamedOptionDefinition(null, "flow-direction", 1, 1, null);
  public static final NamedOptionDefinition OPT_DEFAULT = new NamedOptionDefinition(null, "default", 0, 1, null);
  public static final NamedOptionDefinition OPT_CURRENT = new NamedOptionDefinition(null, "current", 0, 1, null);
  public static final NamedOptionDefinition OPT_FLOW_COMPS = new NamedOptionDefinition(null, "flow-components", -1);
  public static final NamedOptionDefinition OPT_FLOW_ALL_COMPS = new NamedOptionDefinition(null, "flow-all-components", 0);
  public static final NamedOptionDefinition OPT_COMPONENT = new NamedOptionDefinition("C", "component", 1);
  public static final NamedOptionDefinition OPT_SAME_AS_WORKSPACE = new NamedOptionDefinition("s", "same-as-workspace", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(new ContinuousGroup()
      .addOption(OPT_COMPONENT, Messages.FlowTargetCmd2Options_COMPONENT_HELP, false)
      .addOption(OPT_SAME_AS_WORKSPACE, Messages.FlowTargetCmdOptions_SAME_AS_WORKSPACE_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_FLOW_ALL_COMPS, Messages.FlowTargetCmdOptions_FLOW_ALL_COMPONENTS_HELP, false)
      .addOption(OPT_FLOW_COMPS, Messages.FlowTargetCmdOptions_FLOW_COMPONENTS_HELP, false))
      .addOption(OPT_FLOW_DIRECTION, Messages.FlowTargetCmdOptions_FLOW_DIRECTION_HELP, false)
      .addOption(OPT_DEFAULT, Messages.FlowTargetCmdOptions_DEFAULT_HELP, false)
      .addOption(OPT_CURRENT, Messages.FlowTargetCmdOptions_CURRENT_HELP, false)
      .addOption(CommonOptions.OPT_POSITIONAL_ARG_SEPARATOR, NLS.bind(Messages.PositionalArgSeparator_Help, OPT_FLOW_COMPS.getName()), false))
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@"), Messages.FlowTargetCmdOptions_SOURCEWS_HELP)
      .addOption(OPT_TARGET, NLS.bind(Messages.FlowTargetCmd2Options_TARGETWS_HELP, OPT_SAME_AS_WORKSPACE.getName()));
    
    return options;
  }
  



  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    boolean setFlowTargetDetails = (cli.hasOption(OPT_FLOW_ALL_COMPS)) || 
      (cli.hasOption(OPT_FLOW_COMPS)) || (cli.hasOption(OPT_DEFAULT)) || 
      (cli.hasOption(OPT_CURRENT)) || (cli.hasOption(OPT_FLOW_DIRECTION));
    boolean setFlowTarget = !setFlowTargetDetails;
    
    if ((cli.hasOption(OPT_COMPONENT)) && (setFlowTargetDetails)) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.FlowTargetCmd2_COMPONENT_MUTUALLY_EXCLUSIVE, 
        new String[] { OPT_CURRENT.getName(), OPT_DEFAULT.getName(), 
        OPT_FLOW_DIRECTION.getName(), 
        OPT_FLOW_COMPS.getName(), OPT_FLOW_ALL_COMPS.getName(), 
        OPT_COMPONENT.getName() }));
    }
    
    if ((cli.hasOption(OPT_FLOW_ALL_COMPS)) && (cli.hasOption(OPT_FLOW_COMPS))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_2_ARGUMENTS, OPT_FLOW_ALL_COMPS.getName(), OPT_FLOW_COMPS.getName()));
    }
    
    if (cli.hasOption(OPT_FLOW_DIRECTION)) {
      String value = cli.getOption(OPT_FLOW_DIRECTION);
      if ((!value.equalsIgnoreCase("i")) && (!value.equalsIgnoreCase("o")) && (!value.equalsIgnoreCase("b"))) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.FlowtargetCmd_INVALID_OPTION_VALUE, OPT_FLOW_DIRECTION.getName()));
      }
      
      if ((cli.hasOption(OPT_COMPONENT)) && ((value.equalsIgnoreCase("i")) || (value.equalsIgnoreCase("o")))) {
        throw StatusHelper.argSyntax(Messages.FlowtargetCmd_INVALID_COMPONENT_FLOW);
      }
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
    if ((!cli.hasOption(OPT_TARGET)) && (!cli.hasOption(OPT_SAME_AS_WORKSPACE))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.FlowtargetCmd_MISSING_REQUIRED_TARGET, OPT_SAME_AS_WORKSPACE.getName()));
    }
    if ((!cli.hasOption(OPT_COMPONENT)) && (cli.hasOption(OPT_SAME_AS_WORKSPACE))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.FlowtargetCmd_SAME_AS_WORKSPACE_INVALID_OPTION, OPT_SAME_AS_WORKSPACE.getName(), OPT_COMPONENT.getName()));
    }
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    client = SubcommandUtil.setupDaemon(config);
    

    IScmCommandLineArgument srcSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(srcSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, srcSelector);
    IWorkspace wsFound = RepoUtil.getWorkspace(srcSelector.getItemSelector(), true, true, repo, config);
    ParmsWorkspace ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    

    ParmsWorkspace targetWs = null;
    IScmCommandLineArgument targetSelector = null;
    if (!cli.hasOption(OPT_SAME_AS_WORKSPACE)) {
      targetSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_TARGET), config);
      SubcommandUtil.validateArgument(targetSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      ITeamRepository repo2 = RepoUtil.loginUrlArgAncestor(config, client, targetSelector);
      wsFound = RepoUtil.getWorkspace(targetSelector.getItemSelector(), true, true, repo2, config);
      targetWs = new ParmsWorkspace(repo2.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    }
    
    if (setFlowTarget) {
      if (cli.hasOption(OPT_COMPONENT)) {
        IScmCommandLineArgument compSelector = ScmCommandLineArgument.create(
          cli.getOptionValue(OPT_COMPONENT), config);
        
        if (cli.hasOption(OPT_SAME_AS_WORKSPACE)) {
          FlowComponentTargetCmd.setComponentFlowTarget(ws, null, compSelector, client, config);
        } else {
          FlowComponentTargetCmd.setComponentFlowTarget(ws, targetWs, compSelector, client, config);
        }
      } else {
        FlowWorkspaceTargetCmd.setWorkspaceFlowTarget(ws, targetWs, client, config);
      }
    } else {
      setFlowTargetDetails(ws, targetSelector);
    }
  }
  
  private void setFlowTargetDetails(ParmsWorkspace ws, IScmCommandLineArgument targetSelector) throws FileSystemException
  {
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
      peformUpdate(wsDetails, (FlowInfo)flowItemIdToInfo.get(targetAlias.getUuid().getUuidValue()));
      return;
    }
    

    List<String> flowList = (List)flowNameToItemId.get(targetSelector.getItemSelector());
    if (flowList != null) {
      if (flowList.size() == 1) {
        peformUpdate(wsDetails, (FlowInfo)flowItemIdToInfo.get(flowList.get(0)));
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
  
  private void peformUpdate(WorkspaceDetailsDTO wsDetails, FlowInfo flowInfo) throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    ParmsWorkspace ws = new ParmsWorkspace(wsDetails.getRepositoryURL(), wsDetails.getItemId());
    

    if ((ws == null) && (cli.hasOption(OPT_FLOW_COMPS))) {
      throw StatusHelper.disallowed(Messages.FlowTargetCmd_TARGET_NOT_LOGGED_IN);
    }
    
    List<String> compIds = null;
    if (cli.hasOption(OPT_FLOW_COMPS)) {
      List<IScmCommandLineArgument> compSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_FLOW_COMPS), config);
      SubcommandUtil.validateArgument(compSelectors, RepoUtil.ItemType.COMPONENT);
      compIds = getComponentItemIds(entryDTO, compSelectors);
    }
    
    String flowDirectionValue = cli.hasOption(OPT_FLOW_DIRECTION) ? cli.getOption(OPT_FLOW_DIRECTION, "b") : null;
    boolean incomingFlow = flowDirectionValue != null;
    boolean outgoingFlow = flowDirectionValue != null;
    String defaultValue = cli.hasOption(OPT_DEFAULT) ? cli.getOption(OPT_DEFAULT, "b") : null;
    boolean defaultIncoming = defaultValue != null;
    boolean defaultOutgoing = defaultValue != null;
    String currentValue = cli.hasOption(OPT_CURRENT) ? cli.getOption(OPT_CURRENT, "b") : null;
    boolean currentIncoming = currentValue != null;
    boolean currentOutgoing = currentValue != null;
    
    ParmsWorkspace defaultIncomingFlow = null;ParmsWorkspace defaultOutgoingFlow = null;ParmsWorkspace currentIncomingFlow = null;ParmsWorkspace currentOutgoingFlow = null;
    
    if ((incomingFlow) && (!outgoingFlow)) {
      if ((!defaultOutgoing) && (entryDTO.isDefaultOutgoingFlow())) {
        defaultOutgoingFlow = ws;
      }
      if ((!currentOutgoing) && (entryDTO.isCurrentOutgoingFlow())) {
        currentOutgoingFlow = ws;
      }
    }
    if ((!incomingFlow) && (outgoingFlow)) {
      if ((!defaultIncoming) && (entryDTO.isDefaultIncomingFlow())) {
        defaultIncomingFlow = ws;
      }
      if ((!currentIncoming) && (entryDTO.isCurrentIncomingFlow())) {
        currentIncomingFlow = ws;
      }
    }
    

    ParmsWorkspace flowWs = new ParmsWorkspace(entryDTO.getRepositoryURL(), entryDTO.getWorkspaceItemId());
    if (defaultIncoming) {
      defaultIncomingFlow = flowWs;
      if (!incomingFlow) {
        incomingFlow = true;
        outgoingFlow = outgoingFlow ? true : entryDTO.isOutgoingFlow();
      }
      if ((!defaultOutgoing) && (entryDTO.isDefaultOutgoingFlow())) {
        defaultOutgoingFlow = ws;
      }
    }
    if (defaultOutgoing) {
      defaultOutgoingFlow = flowWs;
      if (!outgoingFlow) {
        outgoingFlow = true;
        incomingFlow = incomingFlow ? true : entryDTO.isIncomingFlow();
      }
      if ((!defaultIncoming) && (entryDTO.isDefaultIncomingFlow())) {
        defaultIncomingFlow = ws;
      }
    }
    if (currentIncoming) {
      currentIncomingFlow = flowWs;
      if (!incomingFlow) {
        incomingFlow = true;
        outgoingFlow = outgoingFlow ? true : entryDTO.isOutgoingFlow();
      }
      if ((!currentOutgoing) && (entryDTO.isCurrentOutgoingFlow())) {
        currentOutgoingFlow = ws;
      }
    }
    if (currentOutgoing) {
      currentOutgoingFlow = flowWs;
      if (!outgoingFlow) {
        outgoingFlow = true;
        incomingFlow = incomingFlow ? true : entryDTO.isIncomingFlow();
      }
      if ((!currentIncoming) && (entryDTO.isCurrentIncomingFlow())) {
        currentIncomingFlow = ws;
      }
    }
    
    updateFlowTarget(ws, flowWs, incomingFlow, outgoingFlow, defaultIncomingFlow, defaultOutgoingFlow, currentIncomingFlow, currentOutgoingFlow, cli.hasOption(OPT_FLOW_ALL_COMPS), compIds);
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
  
  private void updateFlowTarget(ParmsWorkspace ws, ParmsWorkspace flowWs, boolean setIncomingFlow, boolean setOutgoingFlow, ParmsWorkspace defaultIncomingFlow, ParmsWorkspace defaultOutgoingFlow, ParmsWorkspace currentIncomingFlow, ParmsWorkspace currentOutgoingFlow, boolean allComps, List<String> compIds)
    throws FileSystemException
  {
    ParmsPutWorkspace parms = new ParmsPutWorkspace();
    workspace = ws;
    defaultIncomingFlowTarget = defaultIncomingFlow;
    defaultOutgoingFlowTarget = defaultOutgoingFlow;
    currentIncomingFlowTarget = currentIncomingFlow;
    currentOutgoingFlowTarget = currentOutgoingFlow;
    
    if ((allComps) || (compIds != null) || (setIncomingFlow) || (setOutgoingFlow)) {
      flowTargets = new ParmsFlowTargetChange[1];
      ParmsFlowTargetChange flowTargetChange = new ParmsFlowTargetChange();
      workspace = flowWs;
      
      if (compIds != null) {
        scopedComponentItemIds = ((String[])compIds.toArray(new String[compIds.size()]));
      }
      if (allComps) {
        scopedComponentItemIds = new String[0];
      }
      
      if ((setIncomingFlow) && (setOutgoingFlow)) {
        flowDirection = "incoming_outgoing";
      } else if (setIncomingFlow) {
        flowDirection = "incoming";
      } else if (setOutgoingFlow) {
        flowDirection = "outgoing";
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
  
  class FlowInfo
  {
    public IWorkspace ws;
    public WorkspaceFlowEntryDTO entryDTO;
    
    FlowInfo() {}
  }
}
