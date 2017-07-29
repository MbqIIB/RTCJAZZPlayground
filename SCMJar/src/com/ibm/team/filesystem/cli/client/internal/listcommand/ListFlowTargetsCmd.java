package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceFlowEntryDTO;
import com.ibm.team.repository.client.ITeamRepository;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;






public class ListFlowTargetsCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public ListFlowTargetsCmd() {}
  
  public static final NamedOptionDefinition OPT_SHOW_SCOPED_COMPONENTS = new NamedOptionDefinition("S", "show-scoped-components", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(OPT_SHOW_SCOPED_COMPONENTS, Messages.ListFlowTargetsCmd_SHOW_SCOPED_COMPONENTS)
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@"), Messages.ListFlowTargetsCmdOptions_WORKSPACE_HELP);
    
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    

    IScmCommandLineArgument srcSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(srcSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, srcSelector);
    IWorkspace wsFound = RepoUtil.getWorkspace(srcSelector.getItemSelector(), true, true, repo, config);
    ParmsWorkspace ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    
    WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
    
    JSONArray jFlowTargetArray = new JSONArray();
    
    List<WorkspaceFlowEntryDTO> wsFlowList = wsDetails.getFlowEntries();
    JSONObject jFlowTarget; for (WorkspaceFlowEntryDTO wsFlow : wsFlowList) {
      jFlowTarget = new JSONObject();
      
      ITeamRepository flowRepo = null;
      try {
        flowRepo = RepoUtil.getSharedRepository(wsFlow.getRepositoryURL(), true);
      }
      catch (IllegalArgumentException localIllegalArgumentException) {}
      


      if (flowRepo == null) {
        try {
          flowRepo = RepoUtil.login(config, client, config.getConnectionInfo(wsFlow.getRepositoryURL(), null, false, true, false));
        }
        catch (Exception localException1) {}
      }
      

      jFlowTarget.put("uuid", wsFlow.getWorkspaceItemId());
      jFlowTarget.put("url", wsFlow.getRepositoryURL());
      
      String flowName = wsFlow.getWorkspaceItemId();
      IWorkspace flowWs = null;
      if (flowRepo != null) {
        try {
          flowWs = RepoUtil.getWorkspace(wsFlow.getWorkspaceItemId(), true, true, flowRepo, config);
        }
        catch (Exception localException2) {}
      }
      

      if (flowWs != null) {
        flowName = flowWs.getName();
        jFlowTarget.put("type", flowWs.isStream() ? RepoUtil.ItemType.STREAM.toString() : RepoUtil.ItemType.WORKSPACE.toString());
        

        if (cli.hasOption(OPT_SHOW_SCOPED_COMPONENTS))
        {
          JSONArray jCompArray = new JSONArray();
          if (!wsFlow.getScopedComponentItemIds().isEmpty()) {
            Map<String, String> compIdToName = new HashMap();
            

            WorkspaceDetailsDTO flowTargetDetails = 
            
              (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(new ParmsWorkspace(wsFlow.getRepositoryURL(), wsFlow.getWorkspaceItemId())), client, config).get(0);
            

            for (WorkspaceComponentDTO compDTO : flowTargetDetails.getComponents()) {
              compIdToName.put(compDTO.getItemId(), compDTO.getName());
            }
            
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
          }
          jFlowTarget.put("components", jCompArray);
        }
      }
      else {
        jFlowTarget.put("type", RepoUtil.ItemType.UNKNOWN.toString());
      }
      
      jFlowTarget.put("name", flowName);
      jFlowTarget.put("incoming-flow", wsFlow.isIncomingFlow() ? Boolean.TRUE : Boolean.FALSE);
      jFlowTarget.put("outgoing-flow", wsFlow.isOutgoingFlow() ? Boolean.TRUE : Boolean.FALSE);
      jFlowTarget.put("default-incoming", wsFlow.isDefaultIncomingFlow() ? Boolean.TRUE : Boolean.FALSE);
      jFlowTarget.put("default-outgoing", wsFlow.isDefaultOutgoingFlow() ? Boolean.TRUE : Boolean.FALSE);
      jFlowTarget.put("current-incoming", wsFlow.isCurrentIncomingFlow() ? Boolean.TRUE : Boolean.FALSE);
      jFlowTarget.put("current-outgoing", wsFlow.isCurrentOutgoingFlow() ? Boolean.TRUE : Boolean.FALSE);
      jFlowTarget.put("scoped", wsFlow.getScopedComponentItemIds().isEmpty() ? Boolean.FALSE : Boolean.TRUE);
      
      jFlowTargetArray.add(jFlowTarget);
    }
    

    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jFlowTargetArray);
      return;
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    for (Object obj : jFlowTargetArray) {
      JSONObject jFlowTarget = (JSONObject)obj;
      printFlowTarget(jFlowTarget, out);
    }
    
    if (jFlowTargetArray.isEmpty()) {
      out.println(Messages.ListFlowTargetsCmd_NO_TARGETS);
    }
  }
  
  public static void printFlowTarget(JSONObject jFlowTarget, IndentingPrintStream out) {
    String name = (String)jFlowTarget.get("name");
    String itemId = (String)jFlowTarget.get("uuid");
    String repoUri = (String)jFlowTarget.get("url");
    RepoUtil.ItemType itemType = RepoUtil.ItemType.valueOf((String)jFlowTarget.get("type"));
    
    StringBuilder flowInfo = new StringBuilder(AliasUtil.selector(name, UUID.valueOf(itemId), repoUri, itemType));
    if (((Boolean)jFlowTarget.get("scoped")).booleanValue()) {
      flowInfo.append(" ").append(Messages.WorkspacePropertiesCmd_SCOPED);
    }
    
    boolean incomingFlow = ((Boolean)jFlowTarget.get("incoming-flow")).booleanValue();
    boolean outgoingFlow = ((Boolean)jFlowTarget.get("outgoing-flow")).booleanValue();
    if ((incomingFlow) && (outgoingFlow)) {
      flowInfo.append(" ").append(Messages.WorkspacePropertiesCmd_INCOMING_OUTGOING);
    } else {
      if (incomingFlow) {
        flowInfo.append(" ").append(Messages.WorkspacePropertiesCmd_INCOMING);
      }
      
      if (outgoingFlow) {
        flowInfo.append(" ").append(Messages.WorkspacePropertiesCmd_OUTGOING);
      }
    }
    
    boolean defaultIncomingFlow = ((Boolean)jFlowTarget.get("default-incoming")).booleanValue();
    boolean defaultOutgoingFlow = ((Boolean)jFlowTarget.get("default-outgoing")).booleanValue();
    if ((defaultIncomingFlow) && (defaultOutgoingFlow)) {
      flowInfo.append(" ").append(Messages.WorkspacePropertiesCmd_DEFAULT);
    } else {
      if (defaultIncomingFlow) {
        if ((incomingFlow) && (!outgoingFlow)) {
          flowInfo.append(" ").append(Messages.WorkspacePropertiesCmd_DEFAULT);
        } else {
          flowInfo.append(" ").append(Messages.WorkspacePropertiesCmd_DEFAULT_INCOMING);
        }
      }
      if (defaultOutgoingFlow) {
        if ((outgoingFlow) && (!incomingFlow)) {
          flowInfo.append(" ").append(Messages.WorkspacePropertiesCmd_DEFAULT);
        } else {
          flowInfo.append(" ").append(Messages.WorkspacePropertiesCmd_DEFAULT_OUTGOING);
        }
      }
    }
    
    boolean currentIncomingFlow = ((Boolean)jFlowTarget.get("current-incoming")).booleanValue();
    boolean currentOutgoingFlow = ((Boolean)jFlowTarget.get("current-outgoing")).booleanValue();
    if ((currentIncomingFlow) && (currentOutgoingFlow)) {
      flowInfo.append(" ").append(Messages.WorkspacePropertiesCmd_CURRENT);
    } else {
      if (currentIncomingFlow) {
        if ((incomingFlow) && (!outgoingFlow)) {
          flowInfo.append(" ").append(Messages.WorkspacePropertiesCmd_CURRENT);
        } else {
          flowInfo.append(" ").append(Messages.WorkspacePropertiesCmd_CURRENT_INCOMING);
        }
      }
      if (currentOutgoingFlow) {
        if ((outgoingFlow) && (!incomingFlow)) {
          flowInfo.append(" ").append(Messages.WorkspacePropertiesCmd_CURRENT);
        } else {
          flowInfo.append(" ").append(Messages.WorkspacePropertiesCmd_CURRENT_OUTGOING);
        }
      }
    }
    
    out.println(flowInfo.toString());
    
    JSONArray jCompArray = (JSONArray)jFlowTarget.get("components");
    if (jCompArray != null) {
      if (!jCompArray.isEmpty()) {
        out.println(Messages.FlowTargetCmd_FLOWS_TO_COMPONENTS);
        for (Object compObj : jCompArray) {
          JSONObject jComp = (JSONObject)compObj;
          
          name = (String)jComp.get("name");
          itemId = (String)jComp.get("uuid");
          
          out.indent().println(AliasUtil.selector(name, UUID.valueOf(itemId), repoUri, RepoUtil.ItemType.COMPONENT));
        }
      } else {
        out.println(Messages.FlowTargetCmd_FLOWS_ALL_COMPONENTS);
      }
    }
  }
}
