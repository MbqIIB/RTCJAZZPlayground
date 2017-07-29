package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.changeset.ChangesetAssociateWorkitemCmd;
import com.ibm.team.filesystem.cli.client.internal.changeset.ChangesetCommentCmd;
import com.ibm.team.filesystem.cli.client.internal.changeset.ChangesetCompleteCmd;
import com.ibm.team.filesystem.cli.core.internal.ScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.ITypedPreferenceRegistry;
import com.ibm.team.filesystem.cli.core.util.ChangeSetStateFactory;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.FileSystemStatusException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.ISandbox;
import com.ibm.team.filesystem.client.IShareable;
import com.ibm.team.filesystem.client.IUnmodifiedInfo;
import com.ibm.team.filesystem.client.ResourceType;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.internal.SharingManager;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBackupDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsChangeSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCheckInChanges;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCheckInOptions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCheckInShareablesRequest;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCheckInVersionablesRequest;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCommitDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeletedContentDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsEncodingDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLineDelimiterDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLineDelimiterErrorInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsOutOfSyncInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxPaths;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.CheckInResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.CommitDilemmaDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.SandboxUpdateDilemmaDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.UpdateDilemmaDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.EncodingErrorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.LineDelimiterErrorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.SandboxPathsResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.SymlinkWarningDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ComponentSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.SyncViewDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.WorkspaceSyncDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.LogFactory;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.CompletedChangeSetException;
import com.ibm.team.scm.common.IVersionableHandle;
import com.ibm.team.scm.common.ItemAlreadyInActiveChangeSetException;
import com.ibm.team.scm.common.VersionablePermissionDeniedException;
import com.ibm.team.workitem.common.model.IWorkItem;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.osgi.util.NLS;





public class CheckInCmd
  extends AbstractSubcommand
{
  private static final Log log = LogFactory.getLog(CheckInCmd.class.getName());
  int workitemNumber = -1;
  String wiRepoUri;
  public CheckInCmd() {}
  
  static enum DelimiterControl { FAIL_ON_ERROR, 
    

    SET_NO_DELIM, 
    



    REWRITE;
  }
  




  DelimiterControl delimPolicy = DelimiterControl.FAIL_ON_ERROR;
  
  public void run() throws FileSystemException {
    ResourcesPlugin.getWorkspace().getRoot();
    ICommandLine subargs = config.getSubcommandCommandLine();
    
    if (!subargs.hasOption(CheckInCmdOptions.OPT_TO_COMMIT)) {
      throw StatusHelper.argSyntax(Messages.CheckInCmd_0);
    }
    
    String wi = null;
    
    if (subargs.hasOption(CheckInCmdOptions.OPT_WORKITEM)) {
      IScmCommandLineArgument wiSelector = ScmCommandLineArgument.create(subargs.getOptionValue(CheckInCmdOptions.OPT_WORKITEM.getId()), config, false);
      wiRepoUri = wiSelector.getRepositorySelector();
      wi = wiSelector.getItemSelector();
    }
    

    if (wi != null) {
      try {
        workitemNumber = Integer.parseInt(wi);
      } catch (NumberFormatException localNumberFormatException) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.ChangesetAssociateWorkitemCmd_2, wi));
      }
    }
    
    config.setEnableJSON(subargs.hasOption(CommonOptions.OPT_JSON));
    

    if ((subargs.hasOption(CheckInCmdOptions.OPT_DELIM_CONSISTENT)) && 
      (subargs.hasOption(CheckInCmdOptions.OPT_DELIM_NONE))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.CheckInCmd_MAY_NOT_SPECIFY_BOTH_DELIM_ARGS, 
        CheckInCmdOptions.OPT_DELIM_CONSISTENT.toString(), CheckInCmdOptions.OPT_DELIM_NONE.toString()));
    }
    
    if ((subargs.hasOption(CheckInCmdOptions.OPT_COMMIT_DELETED_CONTENT)) && 
      (subargs.hasOption(CheckInCmdOptions.OPT_SKIP_DELETED_CONTENT))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.CheckInCmd_MAY_NOT_SPECIFY_BOTH_DELIM_ARGS, 
        CheckInCmdOptions.OPT_COMMIT_DELETED_CONTENT.toString(), 
        CheckInCmdOptions.OPT_SKIP_DELETED_CONTENT.toString()));
    }
    
    if (subargs.hasOption(CheckInCmdOptions.OPT_DELIM_CONSISTENT)) {
      delimPolicy = DelimiterControl.REWRITE;
    }
    else if (subargs.hasOption(CheckInCmdOptions.OPT_DELIM_NONE)) {
      delimPolicy = DelimiterControl.SET_NO_DELIM;
    }
    
    List<String> paths = subargs.getOptions(CheckInCmdOptions.OPT_TO_COMMIT);
    IScmCommandLineArgument csSelector = ScmCommandLineArgument.create(subargs.getOptionValue(CheckInCmdOptions.OPT_COMMIT_TARGET, null), config);
    SubcommandUtil.validateArgument(csSelector, RepoUtil.ItemType.CHANGESET);
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = null;
    
    ChangeSetSyncDTO changeset = null;
    if (csSelector != null) {
      repo = RepoUtil.loginUrlArgAncestor(config, client, csSelector);
      changeset = RepoUtil.findChangeSet(csSelector.getItemSelector(), false, null, null, repo.getRepositoryURI(), 
        client, config);
      
      if (!changeset.isIsActive()) {
        throw StatusHelper.inappropriateArgument(NLS.bind(Messages.CheckInCmd_30, csSelector.getItemSelector()));
      }
    }
    

    ParmsCheckInChanges changes = new ParmsCheckInChanges();
    List<ParmsWorkspace> wsList = generateParams(changes, paths, changeset, client, config);
    
    CheckInResultDTO result = null;
    boolean retried = false;
    for (;;) {
      try {
        result = client.postCheckInChanges(changes, null);
      } catch (ItemAlreadyInActiveChangeSetException e) {
        throw StatusHelper.disallowed(e.getLocalizedMessage());
      } catch (CompletedChangeSetException e) {
        throw StatusHelper.inappropriateArgument(e.getLocalizedMessage());
      } catch (FileSystemStatusException e) {
        err = new IndentingPrintStream(config.getContext().stderr());
        
        if (e.getData() != null) {
          IStatus status = (IStatus)e.getData();
          IStatus[] arrayOfIStatus; int j = (arrayOfIStatus = status.getChildren()).length;int i = 0; continue;IStatus childStatus = arrayOfIStatus[i];
          err.indent().println(childStatus.getMessage());
          Throwable ex = childStatus.getException();
          if (ex != null) {
            err.indent().indent().println(ex.getLocalizedMessage());
          }
          i++; if (i < j) {
            continue;
          }
          




          throw StatusHelper.malformedInput(status.getMessage());
        }
        
        throw StatusHelper.wrap(Messages.CheckInCmd_8, e, err, null);
      } catch (VersionablePermissionDeniedException e) {
        throw StatusHelper.permissionFailure(e.getLocalizedMessage() != null ? 
          e.getLocalizedMessage() : Messages.Common_VERSIONABLE_PERMISSSION_DENIED);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.CheckInCmd_8, e, new IndentingPrintStream(config.getContext().stderr()), null);
      }
      

      if ((retried) || (!result.isCancelled()) || (result.getCommitDilemma().getLineDelimiterFailures().size() <= 0) || 
        (delimPolicy == DelimiterControl.FAIL_ON_ERROR)) break;
      setLineDelimPolicyForFailures(result.getCommitDilemma().getLineDelimiterFailures(), changes);
      retried = true;
    }
    




    processResult1(result, wsList, client, subargs);
    
    Map<WorkspaceSyncDTO, List<ChangeSetSyncDTO>> wsToChangeSets = getWsToChangesetMap(
      result.getChangeSetsCommitted(), wsList, client);
    
    try
    {
      setChangesetInfo(result, wsList, client, subargs);
    }
    finally {
      processResult2(result, wsToChangeSets, client, subargs);
    }
  }
  
  private void setLineDelimPolicyForFailures(List<LineDelimiterErrorDTO> lineDelimiterFailures, ParmsCheckInChanges parmsChanges)
  {
    List<ParmsLineDelimiterErrorInstructions> parmsLDInstrs = 
      new ArrayList(lineDelimiterFailures.size());
    for (LineDelimiterErrorDTO ldError : lineDelimiterFailures) {
      ParmsLineDelimiterErrorInstructions ldInstr = new ParmsLineDelimiterErrorInstructions();
      sandboxPath = ldError.getShare().getSandboxPath();
      filePath = StringUtil.createPathString(ldError.getFileName().getSegments());
      forceConsistentDelimiters = Boolean.valueOf(delimPolicy == DelimiterControl.REWRITE);
      
      parmsLDInstrs.add(ldInstr);
    }
    
    commitDilemmaHandler.lineDelimiterDilemmaHandler.lineDelimiterErrorInstructions = 
      ((ParmsLineDelimiterErrorInstructions[])parmsLDInstrs.toArray(new ParmsLineDelimiterErrorInstructions[parmsLDInstrs.size()]));
  }
  
  private void processResult1(CheckInResultDTO result, List<ParmsWorkspace> wsList, IFilesystemRestClient client, ICommandLine cli)
    throws FileSystemException
  {
    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
    
    if (result.isCancelled()) {
      if (result.getOutOfSyncShares().size() > 0) {
        AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
      }
      
      if (result.getCommitDilemma().getLineDelimiterFailures().size() > 0) {
        err.println(Messages.CheckInCmd_17);
        for (LineDelimiterErrorDTO ldError : result.getCommitDilemma().getLineDelimiterFailures()) {
          StringBuffer printBuffer = new StringBuffer(
            StringUtil.createPathString(ldError.getFileName().getSegments()));
          printBuffer.append(" ");
          printBuffer.append(NLS.bind(Messages.Common_SURROUND_PARANTHESIS, ldError.getLineDelimiter()));
          err.indent().println(printBuffer);
        }
        
        throw StatusHelper.malformedInput(Messages.CheckInCmd_7);
      }
      
      if (result.getCommitDilemma().getEncodingFailures().size() > 0) {
        err.println(Messages.CheckInCmd_18);
        for (EncodingErrorDTO encError : result.getCommitDilemma().getEncodingFailures()) {
          StringBuffer printBuffer = new StringBuffer(
            StringUtil.createPathString(encError.getShareable().getRelativePath().getSegments()));
          printBuffer.append(" ");
          printBuffer.append(NLS.bind(Messages.Common_SURROUND_PARANTHESIS, encError.getEncoding()));
          err.indent().println(printBuffer);
        }
        
        throw StatusHelper.malformedInput(Messages.CheckInCmd_7);
      }
      
      if (result.getCommitDilemma().getPredecessorDeletedShareables().size() > 0) {
        err.println(Messages.CheckInCmd_PREDECESSOR_DELETED);
        for (ShareableDTO shareable : result.getCommitDilemma().getPredecessorDeletedShareables()) {
          err.indent().println(StringUtil.createPathString(shareable.getRelativePath().getSegments()));
        }
        err.println(NLS.bind(Messages.CheckInCmd_FORCE_COMMIT_DELETED_CONTENT, 
          CheckInCmdOptions.OPT_COMMIT_DELETED_CONTENT.toString(), 
          CheckInCmdOptions.OPT_SKIP_DELETED_CONTENT.toString()));
        
        throw StatusHelper.malformedInput(Messages.CheckInCmd_PREDECESSOR_DELETED_ERROR);
      }
      
      if (result.getUpdateDilemma().getInaccessibleForUpdate().size() > 0) {
        err.println(Messages.Common_INACCESSIBLE_VERSIONABLE_ITEMS_HEADER);
        for (ShareableDTO shareable : result.getUpdateDilemma().getInaccessibleForUpdate()) {
          err.indent().println(StringUtil.createPathString(shareable.getRelativePath().getSegments()));
        }
        
        throw StatusHelper.permissionFailure(Messages.Common_VERSIONABLE_PERMISSSION_DENIED);
      }
      
      if (result.getCommitDilemma().getNonPatchShareables().size() > 0)
      {
        StringBuffer msg = new StringBuffer();
        msg.append(NLS.bind(Messages.CheckInCmd_CURRENT_MERGE_FAILURE, CheckInCmdOptions.OPT_CURRENT_MERGE.toString()));
        
        if (cli.hasOption(CommonOptions.OPT_VERBOSE)) {
          String NEWLINE = System.getProperty("line.separator");
          for (Object o : result.getCommitDilemma().getNonPatchShareables()) {
            msg.append(NEWLINE);
            ShareableDTO shareable = (ShareableDTO)o;
            msg.append(StringUtil.createPathString(shareable.getRelativePath().getSegments()));
          }
        }
        
        throw StatusHelper.portsInProgress(msg.toString());
      }
      
      if (result.getCommitDilemma().getLocalConflictShareables().size() > 0)
      {
        StringBuffer msg = new StringBuffer();
        msg.append(NLS.bind(Messages.CheckInCmd_LOCAL_CONFLICTS_FAILURE, CheckInCmdOptions.OPT_IGNORE_LOCAL_CONFLICTS.toString()));
        
        if (cli.hasOption(CommonOptions.OPT_VERBOSE)) {
          String NEWLINE = System.getProperty("line.separator");
          for (Object o : result.getCommitDilemma().getLocalConflictShareables()) {
            msg.append(NEWLINE);
            ShareableDTO shareable = (ShareableDTO)o;
            msg.append(StringUtil.createPathString(shareable.getRelativePath().getSegments()));
          }
        }
        
        throw StatusHelper.localConflicts(msg.toString());
      }
    }
  }
  
  private void processResult2(CheckInResultDTO result, Map<WorkspaceSyncDTO, List<ChangeSetSyncDTO>> wsToChangeSets, IFilesystemRestClient client, ICommandLine cli) throws FileSystemException
  {
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    if (result.getChangeSetsCommitted().size() > 0) {
      printCommittedChanges(wsToChangeSets, client);
    }
    
    if ((result.getSandboxUpdateDilemma().getBackedUpToShed().size() > 0) && (!config.isJSONEnabled())) {
      SubcommandUtil.showShedUpdate(Messages.AcceptResultDisplayer_SHED_MESSAGE, out, result.getSandboxUpdateDilemma().getBackedUpToShed());
    }
    
    if ((result.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0) && (!config.isJSONEnabled())) {
      SubcommandUtil.showDeletedContent(result.getSandboxUpdateDilemma().getDeletedContentShareables(), out);
    }
    
    String NEWLINE = System.getProperty("line.separator");
    
    if (result.getCommitDilemma().getNonInteroperableLinks().size() > 0) {
      StringBuffer msg = new StringBuffer();
      msg.append(Messages.CheckInCmd_2);
      msg.append(NEWLINE);
      for (Object o : result.getCommitDilemma().getNonInteroperableLinks()) {
        SymlinkWarningDTO warning = (SymlinkWarningDTO)o;
        
        msg.append(NLS.bind(Messages.CheckInCmd_4, new Object[] {
          warning.getLocation(), 
          warning.getTarget(), 
          warning.getType() }));
        
        msg.append(NEWLINE);
      }
      
      log.warn(msg);
      if (cli.hasOption(CommonOptions.OPT_VERBOSE)) {
        out.print(msg);
      }
    }
    
    if (result.getCommitDilemma().getBrokenLinks().size() > 0) {
      StringBuffer msg = new StringBuffer();
      msg.append(Messages.CheckInCmd_5);
      msg.append(NEWLINE);
      for (Object o : result.getCommitDilemma().getBrokenLinks()) {
        SymlinkWarningDTO warning = (SymlinkWarningDTO)o;
        
        msg.append(NLS.bind(Messages.CheckInCmd_4, new Object[] {
          warning.getLocation(), 
          warning.getTarget(), 
          warning.getType() }));
        
        msg.append(NEWLINE);
      }
      
      out.print(msg);
      log.warn(msg);
    }
  }
  

  private Map<WorkspaceSyncDTO, List<ChangeSetSyncDTO>> getWsToChangesetMap(List<String> changeSetIds, List<ParmsWorkspace> wsList, IFilesystemRestClient client)
    throws FileSystemException
  {
    SyncViewDTO syncView = SubcommandUtil.getSyncView(wsList, false, client, config);
    

    Map<WorkspaceSyncDTO, List<ChangeSetSyncDTO>> wsToChangeSets = new HashMap();
    Iterator localIterator2; for (Iterator localIterator1 = syncView.getWorkspaces().iterator(); localIterator1.hasNext(); 
        localIterator2.hasNext())
    {
      WorkspaceSyncDTO ws = (WorkspaceSyncDTO)localIterator1.next();
      localIterator2 = ws.getComponents().iterator(); continue;ComponentSyncDTO comp = (ComponentSyncDTO)localIterator2.next();
      Iterator localIterator4; for (Iterator localIterator3 = comp.getOutgoingChangeSetsAfterBasis().iterator(); localIterator3.hasNext(); 
          localIterator4.hasNext())
      {
        ChangeSetSyncDTO cs = (ChangeSetSyncDTO)localIterator3.next();
        localIterator4 = changeSetIds.iterator(); continue;String csId = (String)localIterator4.next();
        if (csId.equals(cs.getChangeSetItemId())) {
          List<ChangeSetSyncDTO> csList = (List)wsToChangeSets.get(ws);
          if (csList == null) {
            csList = new ArrayList();
            wsToChangeSets.put(ws, csList);
          }
          

          cs.setComponentItemId(comp.getComponentItemId());
          csList.add(cs);
        }
      }
    }
    


    return wsToChangeSets;
  }
  
  private void printCommittedChanges(Map<WorkspaceSyncDTO, List<ChangeSetSyncDTO>> wsToChangeSets, IFilesystemRestClient client)
    throws FileSystemException
  {
    PendingChangesUtil.PendingChangesOptions opts = new PendingChangesUtil.PendingChangesOptions();
    opts.enableFilter(1);
    opts.enableFilter(4);
    
    opts.enablePrinter(0);
    opts.enablePrinter(1);
    opts.enablePrinter(11);
    opts.enablePrinter(24);
    opts.enablePrinter(25);
    opts.enablePrinter(8);
    opts.enablePrinter(4);
    opts.enablePrinter(23);
    opts.enablePrinter(6);
    if (config.getSubcommandCommandLine().hasOption(CheckInCmdOptions.OPT_WORKITEM)) {
      opts.enablePrinter(7);
    }
    
    opts.setMaxChanges(CommonOptions.getMaxChangesToInterpret(config));
    
    List<ParmsWorkspace> wsList = new ArrayList(wsToChangeSets.keySet().size());
    Iterator localIterator2; for (Iterator localIterator1 = wsToChangeSets.entrySet().iterator(); localIterator1.hasNext(); 
        
        localIterator2.hasNext())
    {
      Map.Entry<WorkspaceSyncDTO, List<ChangeSetSyncDTO>> entry = (Map.Entry)localIterator1.next();
      wsList.add(new ParmsWorkspace(((WorkspaceSyncDTO)entry.getKey()).getRepositoryUrl(), ((WorkspaceSyncDTO)entry.getKey()).getWorkspaceItemId()));
      localIterator2 = ((List)entry.getValue()).iterator(); continue;ChangeSetSyncDTO csSync = (ChangeSetSyncDTO)localIterator2.next();
      opts.addFilter(UUID.valueOf(csSync.getChangeSetItemId()), 4);
      
      if (!opts.isInFilter(UUID.valueOf(csSync.getComponentItemId()), 1)) {
        opts.addFilter(UUID.valueOf(csSync.getComponentItemId()), 1);
      }
    }
    

    JSONArray workspaces = JSONPrintUtil.jsonizePendingChanges3(client, wsList, opts, config);
    
    if (config.getSubcommandCommandLine().hasOption(CheckInCmdOptions.OPT_COMPLETE))
    {

      updateWorkspaces(workspaces, wsToChangeSets, opts, client);
    }
    
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(workspaces);
    } else {
      IndentingPrintStream ps = new IndentingPrintStream(config.getContext().stdout());
      PendingChangesUtil.printWorkspaces(workspaces, opts, ps);
    }
  }
  

  private void updateWorkspaces(JSONArray workspaces, Map<WorkspaceSyncDTO, List<ChangeSetSyncDTO>> wsToChangeSets, PendingChangesUtil.PendingChangesOptions options, IFilesystemRestClient client)
    throws FileSystemException
  {
    Iterator localIterator2;
    
    for (Iterator localIterator1 = workspaces.iterator(); localIterator1.hasNext(); 
        








        localIterator2.hasNext())
    {
      Object workspace = localIterator1.next();
      JSONObject jWs = (JSONObject)workspace;
      String wsItemId = (String)jWs.get("uuid");
      String repoUri = (String)jWs.get("url");
      

      Map<String, List<ChangeSetSyncDTO>> compToChangeSets = null;
      

      JSONArray components = (JSONArray)jWs.get("components");
      localIterator2 = components.iterator(); continue;Object comp = localIterator2.next();
      JSONObject jComp = (JSONObject)comp;
      
      String compItemId = (String)jComp.get("uuid");
      JSONArray outgoingChanges = (JSONArray)jComp.get("outgoing-changes");
      

      if ((outgoingChanges == null) || (outgoingChanges.size() == 0)) {
        if (compToChangeSets == null) {
          compToChangeSets = getCompToChangeSetsMap(wsToChangeSets, wsItemId);
        }
        if (compToChangeSets.containsKey(compItemId))
        {

          List<ChangeSetSyncDTO> csDtoList = (List)compToChangeSets.get(compItemId);
          List<String> csIdList = new ArrayList(csDtoList.size());
          
          for (ChangeSetSyncDTO csDto : csDtoList) {
            csIdList.add(csDto.getChangeSetItemId());
          }
          ChangeSetSyncDTO[] csDTOArray = RepoUtil.findChangeSets(csIdList, true, 
            wsItemId, "workspace", repoUri, client, config);
          

          JSONPrintUtil.jsonizeChangeSets(jComp, "outgoing-changes", 
            Arrays.asList(csDTOArray), new ChangeSetStateFactory(), options, client, config);
        }
      }
    }
  }
  

  private Map<String, List<ChangeSetSyncDTO>> getCompToChangeSetsMap(Map<WorkspaceSyncDTO, List<ChangeSetSyncDTO>> wsToChangeSets, String wsItemId)
  {
    Map<String, List<ChangeSetSyncDTO>> compToChangeSets = new HashMap();
    for (Map.Entry<WorkspaceSyncDTO, List<ChangeSetSyncDTO>> entry : wsToChangeSets.entrySet()) {
      if (((WorkspaceSyncDTO)entry.getKey()).getWorkspaceItemId().equals(wsItemId))
      {
        for (ChangeSetSyncDTO csDTO : (List)entry.getValue()) {
          List<ChangeSetSyncDTO> csList = (List)compToChangeSets.get(csDTO.getComponentItemId());
          if (csList == null) {
            csList = new ArrayList(((List)entry.getValue()).size());
            compToChangeSets.put(csDTO.getComponentItemId(), csList);
          }
          csList.add(csDTO);
        }
      }
    }
    
    return compToChangeSets;
  }
  
  private void setChangesetInfo(CheckInResultDTO result, List<ParmsWorkspace> wsList, IFilesystemRestClient client, ICommandLine subargs)
    throws FileSystemException
  {
    if ((subargs.hasOption(CheckInCmdOptions.OPT_COMMENT)) || 
      (subargs.hasOption(CheckInCmdOptions.OPT_COMPLETE)) || 
      (subargs.hasOption(CheckInCmdOptions.OPT_WORKITEM))) {
      List<String> repos = new ArrayList();
      

      SyncViewDTO syncView = SubcommandUtil.getSyncView(wsList, false, client, config);
      

      List<String> changeSetIds = result.getChangeSetsCommitted();
      Map<WorkspaceSyncDTO, List<ChangeSetSyncDTO>> wsToChangeSets = 
        new HashMap();
      ChangeSetSyncDTO cs; for (WorkspaceSyncDTO ws : syncView.getWorkspaces()) { Iterator localIterator3;
        for (Iterator localIterator2 = ws.getComponents().iterator(); localIterator2.hasNext(); 
            localIterator3.hasNext())
        {
          ComponentSyncDTO comp = (ComponentSyncDTO)localIterator2.next();
          localIterator3 = comp.getOutgoingChangeSetsAfterBasis().iterator(); continue;cs = (ChangeSetSyncDTO)localIterator3.next();
          for (String csId : changeSetIds) {
            if (csId.equals(cs.getChangeSetItemId())) {
              List<ChangeSetSyncDTO> csDTOList = (List)wsToChangeSets.get(ws);
              if (csDTOList == null) {
                csDTOList = new ArrayList();
                wsToChangeSets.put(ws, csDTOList);
              }
              csDTOList.add(cs);
            }
          }
        }
        

        if ((wsToChangeSets.get(ws) != null) && (!repos.contains(ws.getRepositoryId()))) {
          repos.add(ws.getRepositoryId());
        }
      }
      

      List<String> csIds;
      

      for (Map.Entry<WorkspaceSyncDTO, List<ChangeSetSyncDTO>> entry : wsToChangeSets.entrySet()) {
        csIds = new ArrayList();
        

        for (ChangeSetSyncDTO csDTO : (List)entry.getValue()) {
          csIds.add(csDTO.getChangeSetItemId());
          
          if (subargs.hasOption(CheckInCmdOptions.OPT_COMMENT)) {
            String comment = subargs.getOption(CheckInCmdOptions.OPT_COMMENT);
            ChangesetCommentCmd.setComment(csDTO.getChangeSetItemId(), comment, 
              csDTO.getRepositoryUrl(), client, config);
          }
        }
        

        if (subargs.hasOption(CheckInCmdOptions.OPT_COMPLETE)) {
          ChangesetCompleteCmd.setComplete(new ParmsWorkspace(
            ((WorkspaceSyncDTO)entry.getKey()).getRepositoryUrl(), ((WorkspaceSyncDTO)entry.getKey()).getWorkspaceItemId()), 
            csIds, ((WorkspaceSyncDTO)entry.getKey()).getRepositoryUrl(), client, config);
        }
      }
      

      if ((subargs.hasOption(CheckInCmdOptions.OPT_WORKITEM)) && (repos.size() == 1)) {
        IWorkItem workitem = null;
        
        for (csIds = wsToChangeSets.entrySet().iterator(); csIds.hasNext(); 
            cs.hasNext())
        {
          Object entry = (Map.Entry)csIds.next();
          cs = ((List)((Map.Entry)entry).getValue()).iterator(); continue;ChangeSetSyncDTO csDTO = (ChangeSetSyncDTO)cs.next();
          

          if (workitem == null) { ITeamRepository repo;
            ITeamRepository repo; if (wiRepoUri == null) {
              repo = RepoUtil.getSharedRepository(
                ((WorkspaceSyncDTO)((Map.Entry)entry).getKey()).getRepositoryUrl(), true);
            }
            else {
              repo = RepoUtil.login(config, client, config.getConnectionInfo(wiRepoUri));
              
              wiRepoUri = repo.getRepositoryURI();
            }
            workitem = RepoUtil.findWorkItem(workitemNumber, repo, config);
          }
          
          ChangesetAssociateWorkitemCmd.setWorkitem(((WorkspaceSyncDTO)((Map.Entry)entry).getKey()).getWorkspaceItemId(), 
            csDTO.getChangeSetItemId(), workitem.getItemId().getUuidValue(), 
            ((WorkspaceSyncDTO)((Map.Entry)entry).getKey()).getRepositoryUrl(), wiRepoUri, config);
        }
      }
      



      if ((subargs.hasOption(CheckInCmdOptions.OPT_WORKITEM)) && (repos.size() > 1)) {
        ISubcommandDefinition defnTemp = SubcommandUtil.getClassSubCommandDefn(config, 
          ChangesetAssociateWorkitemCmd.class);
        throw StatusHelper.inappropriateArgument(NLS.bind(
          Messages.CheckInCmd_WORKITEM_CANNOT_SPAN_REPOS, new String[] {
          Integer.toString(workitemNumber), config.getContext().getAppName(), 
          SubcommandUtil.getExecutionString(defnTemp).toString() }));
      }
    }
  }
  











  private List<ParmsWorkspace> generateParams(ParmsCheckInChanges changes, List<String> checkinPaths, ChangeSetSyncDTO changeset, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    List<String> pathSelectors = new ArrayList();
    for (String checkinPath : checkinPaths) {
      ILocation path = SubcommandUtil.makeAbsolutePath(config, checkinPath);
      pathSelectors.add(path.toOSString());
    }
    

    ParmsSandboxPaths parmsPaths = new ParmsSandboxPaths();
    includeNonRegisteredSandboxes = true;
    pathsToResolve = ((String[])pathSelectors.toArray(new String[pathSelectors.size()]));
    
    SandboxPathsResultDTO pathsResult = null;
    try {
      pathsResult = client.getSandboxPaths(parmsPaths, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.Common_UNABLE_TO_GET_SANDBOX_PATHS, e, 
        new IndentingPrintStream(config.getContext().stderr()), null);
    }
    

    List<String> reposOfPaths = new ArrayList();
    Map<String, ParmsWorkspace> wsIdToWs = new HashMap();
    Map<String, Set<String>> sandboxToCheckinPaths = new HashMap();
    List<ResourcePropertiesDTO> resources = new ArrayList();
    
    int count = 0;
    for (ShareableDTO shareables : pathsResult.getPaths()) {
      if ((shareables.getSandboxPath() == null) || (shareables.getSandboxPath().length() == 0)) {
        throw StatusHelper.inappropriateArgument(NLS.bind(Messages.Common_PATH_NOT_SHARED, pathSelectors.get(count)));
      }
      
      Set<String> paths = (Set)sandboxToCheckinPaths.get(shareables.getSandboxPath());
      if (paths == null) {
        paths = new HashSet();
        sandboxToCheckinPaths.put(shareables.getSandboxPath(), paths);
      }
      
      if (shareables.getRelativePath().getSegments().size() == 0) {
        List<ShareDTO> shares = RepoUtil.getSharesInSandbox(null, new PathLocation(shareables.getSandboxPath()), 
          client, config);
        if (shares.size() > 0) {
          for (ShareDTO share : shares) {
            if ((changeset == null) || ((changeset != null) && (changeset.getComponentItemId().equals(share.getComponentItemId())))) {
              String absolutePath = new File(share.getSandboxPath(), share.getPath().getSegments().get(0).toString()).getAbsolutePath();
              addResourcesForCheckin(resources, shareables, reposOfPaths, wsIdToWs, changeset, paths, absolutePath, client);
            }
          }
        }
      } else {
        addResourcesForCheckin(resources, shareables, reposOfPaths, wsIdToWs, changeset, paths, (String)pathSelectors.get(count), client);
      }
      
      count++;
    }
    

    SubcommandUtil.registerSandboxes((String[])sandboxToCheckinPaths.keySet().toArray(
      new String[sandboxToCheckinPaths.keySet().size()]), client, config);
    
    config.getSubcommandCommandLine();
    

    for (String repoUri : reposOfPaths) {
      RepoUtil.login(config, client, config.getConnectionInfo(repoUri));
    }
    

    Map<String, ParmsCheckInVersionablesRequest> versionablesToCheckIn = null;
    String comment = config.getSubcommandCommandLine().getOption(CheckInCmdOptions.OPT_COMMENT, "");
    versionablesToCheckIn = createVersionables(resources, client, changeset, comment);
    

    Map<ILocation, Set<ILocation>> parmPaths = SubcommandUtil.getNonOverlappingResources(resources);
    

    recomputeLocalChanges(parmPaths, client);
    

    generateCheckinParms(changes, sandboxToCheckinPaths, changeset, versionablesToCheckIn, comment);
    return new ArrayList(wsIdToWs.values());
  }
  










  private Map<String, ParmsCheckInVersionablesRequest> createVersionables(List<ResourcePropertiesDTO> resources, IFilesystemRestClient client, ChangeSetSyncDTO changeset, String comment)
    throws FileSystemException
  {
    Map<String, ParmsCheckInVersionablesRequest> versionablesToCheckIn = new HashMap();
    for (ResourcePropertiesDTO resource : resources) {
      ShareDTO share = resource.getShare();
      String fullPath = resource.getFullPath();
      ILocation path = new PathLocation(fullPath);
      String repositoryUrl = RepoUtil.getRepoUri(config, client, path);
      ParmsWorkspace ws = new ParmsWorkspace(repositoryUrl, share.getContextItemId());
      if (versionablesToCheckIn.get(share.getComponentItemId()) == null) {
        ParmsCheckInVersionablesRequest aVersionalble = new ParmsCheckInVersionablesRequest();
        workspace = ws;
        componentItemId = share.getComponentItemId();
        if (changeset != null) {
          changeSetItemId = changeset.getChangeSetItemId();
        } else {
          newChangeSetComment = comment;
        }
        versionablesToCheckIn = new String[] { resource.getItemId() };
        versionablesToCheckIn.put(share.getComponentItemId(), aVersionalble);
      }
      else {
        ParmsCheckInVersionablesRequest aVersionalble = (ParmsCheckInVersionablesRequest)versionablesToCheckIn.get(share.getComponentItemId());
        List<String> Ids = new ArrayList(Arrays.asList(versionablesToCheckIn));
        Ids.add(resource.getItemId());
        versionablesToCheckIn = ((String[])Ids.toArray(new String[Ids.size()]));
        versionablesToCheckIn.put(share.getComponentItemId(), aVersionalble);
      }
    }
    return versionablesToCheckIn;
  }
  






  private void recomputeLocalChanges(Map<ILocation, Set<ILocation>> parmPaths, IFilesystemRestClient client)
    throws FileSystemException
  {
    Set<ILocation> locnList = parmPaths.keySet();
    for (ILocation locn : locnList) {
      Set<ILocation> allPaths = (Set)parmPaths.get(locn);
      SubcommandUtil.refreshPaths(locn, new ArrayList(allPaths), client, config);
    }
  }
  
  private void addCheckinPath(ChangeSetSyncDTO changeset, PathDTO path, ShareDTO share, Set<String> paths, List<String> reposOfPaths, Map<String, ParmsWorkspace> wsIdToWs, IFilesystemRestClient client)
    throws FileSystemException
  {
    if ((changeset != null) && (!changeset.getComponentItemId().equals(share.getComponentItemId()))) {
      StringBuffer pathBuffer = new StringBuffer(share.getSandboxPath());
      pathBuffer.append(StringUtil.createPathString(share.getPath().getSegments()));
      
      throw StatusHelper.inappropriateArgument(NLS.bind(Messages.CheckInCmd_26, 
        pathBuffer, changeset.getComponentName()));
    }
    
    if ((path != null) && (paths != null)) {
      paths.add(StringUtil.createPathString(path.getSegments()));
    }
    
    String repositoryUrl = RepoUtil.getRepoUri(config, client, share);
    
    if ((reposOfPaths != null) && 
      (!reposOfPaths.contains(repositoryUrl))) {
      reposOfPaths.add(repositoryUrl);
    }
    
    if (!wsIdToWs.keySet().contains(share.getContextItemId())) {
      wsIdToWs.put(share.getContextItemId(), new ParmsWorkspace(repositoryUrl, 
        share.getContextItemId()));
    }
  }
  










  private void generateCheckinParms(ParmsCheckInChanges parmsChanges, Map<String, Set<String>> sandboxToCheckinPaths, ChangeSetSyncDTO changeset, Map<String, ParmsCheckInVersionablesRequest> versionablesToCheckIn, String comment)
  {
    List<ParmsCheckInShareablesRequest> parmsCheckinShareables = new ArrayList();
    
    for (Map.Entry<String, Set<String>> entry : sandboxToCheckinPaths.entrySet()) {
      ParmsCheckInShareablesRequest checkinRequest = new ParmsCheckInShareablesRequest();
      if (changeset != null) {
        changeSet = new ParmsChangeSet();
        changeSet.changeSetItemId = changeset.getChangeSetItemId();
        changeSet.repositoryUrl = changeset.getRepositoryUrl();
      } else {
        newChangeSetComment = comment;
      }
      sandboxPath = ((String)entry.getKey());
      resourcesToCheckIn = ((String[])((Set)entry.getValue()).toArray(new String[((Set)entry.getValue()).size()]));
      
      parmsCheckinShareables.add(checkinRequest);
    }
    
    versionables = ((ParmsCheckInVersionablesRequest[])versionablesToCheckIn.values().toArray(new ParmsCheckInVersionablesRequest[versionablesToCheckIn.values().size()]));
    paths = ((ParmsCheckInShareablesRequest[])parmsCheckinShareables.toArray(new ParmsCheckInShareablesRequest[parmsCheckinShareables.size()]));
    
    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    
    outOfSyncInstructions = new ParmsOutOfSyncInstructions();
    outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
    outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
    
    sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
    
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
    
    updateDilemmaHandler = new ParmsUpdateDilemmaHandler();
    updateDilemmaHandler.inaccessibleForUpdateInstruction = "cancel";
    
    commitDilemmaHandler = new ParmsCommitDilemmaHandler();
    commitDilemmaHandler.encodingDilemmaHandler = new ParmsEncodingDilemmaHandler();
    commitDilemmaHandler.encodingDilemmaHandler.performDefaultHandling = Boolean.valueOf(true);
    commitDilemmaHandler.encodingDilemmaHandler.generalEncodingErrorInstruction = 
      "cancel";
    commitDilemmaHandler.lineDelimiterDilemmaHandler = new ParmsLineDelimiterDilemmaHandler();
    commitDilemmaHandler.lineDelimiterDilemmaHandler.generalLineDelimiterErrorInstruction = 
      "cancel";
    boolean ignoreLocalConflicts = ((ScmClientConfiguration)config).getSubcommandCommandLine().hasOption(CheckInCmdOptions.OPT_IGNORE_LOCAL_CONFLICTS);
    commitDilemmaHandler.localConflictsInstruction = (ignoreLocalConflicts ? "continue" : "cancel");
    
    if (((ScmClientConfiguration)config).getSubcommandCommandLine().hasOption(CheckInCmdOptions.OPT_COMMIT_DELETED_CONTENT)) {
      commitDilemmaHandler.predecessorContentDeletedInstruction = "continue";
    } else if (((ScmClientConfiguration)config).getSubcommandCommandLine().hasOption(CheckInCmdOptions.OPT_SKIP_DELETED_CONTENT)) {
      commitDilemmaHandler.predecessorContentDeletedInstruction = "no";
    } else {
      commitDilemmaHandler.predecessorContentDeletedInstruction = "cancel";
    }
    
    if (((ScmClientConfiguration)config).getSubcommandCommandLine().hasOption(CheckInCmdOptions.OPT_CURRENT_MERGE)) {
      commitDilemmaHandler.nonPatchShareablesInstruction = "continue";
    } else {
      commitDilemmaHandler.nonPatchShareablesInstruction = "cancel";
    }
    
    int atomicMaximum = ((ScmClientConfiguration)config).getPersistentPreferences().getAtomicCommitMaximum();
    if (atomicMaximum != -1) {
      checkInOptions = new ParmsCheckInOptions();
      checkInOptions.allowNonAtomicCommit = Boolean.valueOf(true);
      checkInOptions.numberOfUploads = atomicMaximum;
    }
  }
  
  private boolean isSandboxRoot(IShareable shareable) {
    return shareable.getSandbox().getRoot().equals(shareable.getFullPath());
  }
  
  private void addResourcesForCheckin(List<ResourcePropertiesDTO> resources, ShareableDTO shareables, List<String> reposOfPaths, Map<String, ParmsWorkspace> wsIdToWs, ChangeSetSyncDTO changeset, Set<String> paths, String pathSelector, IFilesystemRestClient client)
    throws FileSystemException
  {
    ResourcePropertiesDTO resource = RepoUtil.getResourceProperties(pathSelector, SubcommandUtil.shouldRefreshFileSystem(config), client, config, false);
    

    if (!resource.isSetVersionableItemType()) {
      ILocation path = SubcommandUtil.makeAbsolutePath(config, pathSelector);
      

      IShareable parent = SharingManager.getInstance().findShareable(path.getParent(), ResourceType.FOLDER);
      if ((parent == null) || ((parent.getRemote(null) == null) && (!isSandboxRoot(parent)))) {
        throw StatusHelper.inappropriateArgument(NLS.bind(Messages.CheckInCmd_MISSING_SHARED_PARENT, path.toOSString()));
      }
      
      IUnmodifiedInfo childInfo = SharingManager.getInstance().findUnmodifiedInfoForChild(parent, path.getName(), null);
      if ((childInfo == null) && 
        (parent.exists(null))) {
        IShareable shareable = SharingManager.getInstance().findShareable(path, ResourceType.FILE);
        
        if (shareable.exists(null))
        {
          throw StatusHelper.inappropriateArgument(NLS.bind(Messages.CheckInCmd_MISSING_SHARED_PARENT, path.toOSString()));
        }
        
        throw StatusHelper.inappropriateArgument(NLS.bind(Messages.Common_PATH_DOES_NOT_EXIST, path.toOSString()));
      }
      



      resource.setItemId(childInfo.getVersionable().getItemId().getUuidValue());
      resource.setVersionableItemType(SubcommandUtil.getVersionableItemType(childInfo.getVersionable().getItemType()));
      resource.setRemote(true);
    }
    
    String fullPath = resource.getFullPath();
    ILocation ipath = new PathLocation(fullPath);
    
    if ((!SubcommandUtil.exists(ipath, null)) && (resource.isRemote()))
    {
      resources.add(resource);
      addCheckinPath(changeset, null, resource.getShare(), null, null, wsIdToWs, client);
    }
    else
    {
      addCheckinPath(changeset, resource.getPath().getRelativePath(), resource.getShare(), paths, reposOfPaths, wsIdToWs, client);
    }
  }
}
