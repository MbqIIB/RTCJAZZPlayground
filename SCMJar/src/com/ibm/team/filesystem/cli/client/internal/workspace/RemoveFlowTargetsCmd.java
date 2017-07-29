package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil.ItemInfo;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPutWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceFlowEntryDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.osgi.util.NLS;


public class RemoveFlowTargetsCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public RemoveFlowTargetsCmd() {}
  
  public static final PositionalOptionDefinition OPT_TARGETS = new PositionalOptionDefinition("target-workspace", 1, -1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@"), Messages.FlowTargetCmdOptions_WORKSPACE_HELP)
      .addOption(OPT_TARGETS, Messages.RemoveFlowTargetsCmdOptions_TARGET_HELP);
    
    return options;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    

    IScmCommandLineArgument srcSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(srcSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, srcSelector);
    IWorkspace wsFound = RepoUtil.getWorkspace(srcSelector.getItemSelector(), true, true, repo, config);
    ParmsWorkspace ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
    List<WorkspaceFlowEntryDTO> wsFlowList = wsDetails.getFlowEntries();
    

    List<IScmCommandLineArgument> targetSelectorList = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_TARGETS), config);
    SubcommandUtil.validateArgument(targetSelectorList, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    

    Map<String, String> targetItemIdsToSelectors = new HashMap();
    Set<String> targetNames = new HashSet();
    IUuidAliasRegistry.IUuidAlias targetAlias;
    for (IScmCommandLineArgument targetSelector : targetSelectorList) {
      targetAlias = targetSelector.getAlias();
      if (targetAlias != null) {
        targetItemIdsToSelectors.put(targetAlias.getUuid().getUuidValue(), targetSelector.getItemSelector());
      } else {
        targetNames.add(targetSelector.getItemSelector());
      }
    }
    
    List<ParmsWorkspace> targetWsList = new ArrayList();
    
    WorkspaceFlowEntryDTO wsFlow;
    for (String targetItemId : targetItemIdsToSelectors.keySet()) {
      WorkspaceFlowEntryDTO foundTarget = null;
      for (Iterator localIterator2 = wsFlowList.iterator(); localIterator2.hasNext();) { wsFlow = (WorkspaceFlowEntryDTO)localIterator2.next();
        if (targetItemId.equals(wsFlow.getWorkspaceItemId())) {
          foundTarget = wsFlow;
          break;
        }
      }
      
      if (foundTarget == null)
      {
        targetNames.add(targetItemId);
      } else {
        targetWsList.add(new ParmsWorkspace(foundTarget.getRepositoryURL(), foundTarget.getWorkspaceItemId()));
      }
    }
    

    Object unmatchedTargets = new ArrayList();
    if (!targetNames.isEmpty())
    {
      Map<String, List<ParmsWorkspace>> wsNameToWsParms = new HashMap();
      for (WorkspaceFlowEntryDTO wsFlow : wsFlowList) {
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
        

        if (flowRepo != null) {
          IWorkspace flowWs = null;
          try
          {
            flowWs = RepoUtil.getWorkspace(wsFlow.getWorkspaceItemId(), true, true, flowRepo, config);
            List<ParmsWorkspace> wsParmsList = (List)wsNameToWsParms.get(flowWs.getName());
            if (wsParmsList == null) {
              wsParmsList = new ArrayList();
              wsNameToWsParms.put(flowWs.getName(), wsParmsList);
            }
            wsParmsList.add(new ParmsWorkspace(wsFlow.getRepositoryURL(), wsFlow.getWorkspaceItemId()));
          }
          catch (Exception localException2) {}
        }
      }
      


      for (String targetName : targetNames) {
        Object wsParmsList = (List)wsNameToWsParms.get(targetName);
        if (wsParmsList != null) {
          if (((List)wsParmsList).size() == 1) {
            targetWsList.add((ParmsWorkspace)((List)wsParmsList).get(0));
          }
          else {
            List<SubcommandUtil.ItemInfo> flowsMatched = new ArrayList(((List)wsParmsList).size());
            for (ParmsWorkspace wsParms : (List)wsParmsList) {
              try {
                flowsMatched.add(new SubcommandUtil.ItemInfo(targetName, workspaceItemId, repositoryUrl, 
                  wsParms.getWorkspaceConnection(null).getResolvedWorkspace().isStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE));
              } catch (TeamRepositoryException e) {
                throw StatusHelper.wrap(Messages.RemoveFlowTargetsCmd_FAILURE, e, 
                  new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
              }
            }
            SubcommandUtil.displayAmbiguousSelectorException(targetName, flowsMatched, config);
            throw StatusHelper.ambiguousSelector(NLS.bind(Messages.RemoveFlowTargetsCmd_AMBIGUOUS_FLOWTARGET, targetName));
          }
        } else {
          String unmatchedTarget = targetName;
          if (targetItemIdsToSelectors.containsKey(targetName)) {
            unmatchedTarget = (String)targetItemIdsToSelectors.get(targetName);
          }
          ((List)unmatchedTargets).add(unmatchedTarget);
        }
      }
    }
    
    if (!targetWsList.isEmpty()) {
      ParmsPutWorkspace parms = new ParmsPutWorkspace();
      workspace = ws;
      flowTargetsToRemove = ((ParmsWorkspace[])targetWsList.toArray(new ParmsWorkspace[targetWsList.size()]));
      try
      {
        client.postPutWorkspace(parms, null);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.RemoveFlowTargetsCmd_FAILURE, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
      }
    }
    

    if (!((List)unmatchedTargets).isEmpty()) {
      IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
      err.println(Messages.RemoveFlowTargetsCmd_TARGETS_NOT_FOUND_HEADER);
      
      for (String unmatchedTarget : (List)unmatchedTargets) {
        err.indent().println(unmatchedTarget);
      }
      throw StatusHelper.itemNotFound(Messages.RemoveFlowTargetsCmd_TARGETS_NOT_FOUND);
    }
    
    config.getContext().stdout().println(Messages.RemoveFlowTargetsCmd_SUCCESS);
  }
}
