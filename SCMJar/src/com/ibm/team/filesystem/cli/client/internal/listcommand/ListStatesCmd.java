package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.changeset.ChangesetCommonOptions;
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
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.IFileItem;
import com.ibm.team.filesystem.common.internal.rest.IFilesystemRichClientRestService;
import com.ibm.team.filesystem.common.internal.rest.IFilesystemRichClientRestService.ParmsContentStatus;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeFolderSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSyncDTO;
import com.ibm.team.filesystem.common.internal.rich.rest.dto.ContentStatusDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IVersionedContentService.ContentStatus;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmIntermediateChangeNode;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmIntermediateHistory;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsGetIntermediateHistory;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;


public class ListStatesCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public ListStatesCmd() {}
  
  IFilesystemRestClient client = null;
  
  public static final IOptionKey OPT_CHANGES = new OptionKey("changes");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(OPT_CHANGES, "c", "changes", Messages.ListStatesCmdOption_CHANGES, -1)
      .addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.ChangesetCompleteCmdOptions_0)
      .addOption(ChangesetCommonOptions.OPT_CHANGESET, ChangesetCommonOptions.OPT_CHANGESET_HELP);
    
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_WORKSPACE_NAME.getId(), null), config);
    IScmCommandLineArgument csSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_CHANGESET.getId()), config);
    
    client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = null;
    ParmsWorkspace ws = null;
    
    if (wsSelector != null) {
      SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      
      repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
      ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
      

      RepoUtil.validateItemRepos(RepoUtil.ItemType.CHANGESET, Collections.singletonList(csSelector), repo, config);
    } else {
      repo = RepoUtil.loginUrlArgAncestor(config, client, csSelector);
    }
    
    SubcommandUtil.validateArgument(csSelector, RepoUtil.ItemType.CHANGESET);
    

    List<IScmCommandLineArgument> changeSelectors = null;
    if (cli.hasOption(OPT_CHANGES)) {
      changeSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_CHANGES), config);
      SubcommandUtil.validateArgument(changeSelectors, new RepoUtil.ItemType[] { RepoUtil.ItemType.VERSIONABLE });
    }
    

    ChangeSetSyncDTO csDTO = RepoUtil.findChangeSet(csSelector.getItemSelector(), true, 
      ws != null ? workspaceItemId : null, "workspace", repo.getRepositoryURI(), 
      client, config);
    
    List<ChangeSyncDTO> changes = getChanges(csDTO, changeSelectors, repo);
    
    listStates(ws, csDTO, changes, repo);
  }
  
  private List<ChangeSyncDTO> getChanges(ChangeSetSyncDTO csDTO, List<IScmCommandLineArgument> changeSelectors, ITeamRepository repo)
    throws FileSystemException
  {
    List<ChangeSyncDTO> changes = null;
    if ((changeSelectors == null) || (changeSelectors.size() == 0)) {
      changes = new ArrayList();
      Iterator localIterator2; for (Iterator localIterator1 = csDTO.getChanges().iterator(); localIterator1.hasNext(); 
          localIterator2.hasNext())
      {
        ChangeFolderSyncDTO changeFolderSyncDTO = (ChangeFolderSyncDTO)localIterator1.next();
        localIterator2 = changeFolderSyncDTO.getChanges().iterator(); continue;ChangeSyncDTO changeSyncDTO = (ChangeSyncDTO)localIterator2.next();
        changes.add(changeSyncDTO);
      }
    }
    else {
      List<String> selectors = RepoUtil.getSelectors(changeSelectors);
      changes = getChanges(csDTO, selectors, repo.getRepositoryURI(), config);
    }
    
    return changes;
  }
  
  private List<ChangeSyncDTO> getChanges(ChangeSetSyncDTO csDTO, List<String> changeSelectors, String repoUri, IScmClientConfiguration config) throws FileSystemException
  {
    List<ChangeSyncDTO> changes = new ArrayList();
    

    for (String changeSelector : changeSelectors) {
      IUuidAliasRegistry.IUuidAlias changeAlias = RepoUtil.lookupUuidAndAlias(changeSelector, repoUri);
      List<ChangeSyncDTO> matchedChanges = new ArrayList();
      Iterator localIterator3;
      ChangeSyncDTO changeSyncDTO; for (Iterator localIterator2 = csDTO.getChanges().iterator(); localIterator2.hasNext(); 
          localIterator3.hasNext())
      {
        ChangeFolderSyncDTO changeFolderSyncDTO = (ChangeFolderSyncDTO)localIterator2.next();
        localIterator3 = changeFolderSyncDTO.getChanges().iterator(); continue;changeSyncDTO = (ChangeSyncDTO)localIterator3.next();
        
        if ((changeAlias != null) && (changeAlias.getUuid().getUuidValue().equals(changeSyncDTO.getVersionableItemId()))) {
          matchedChanges.add(changeSyncDTO);



        }
        else if (changeAlias == null)
        {
          IPath changeSelectorPath = new Path(changeSelector).makeAbsolute().removeTrailingSeparator();
          if (changeSelectorPath.segmentCount() > 1) {
            IPath changePath = new Path(changeSyncDTO.getPathHint()).makeAbsolute().removeTrailingSeparator();
            if (changePath.toOSString().endsWith(changeSelectorPath.toOSString())) {
              matchedChanges.add(changeSyncDTO);
            }
          }
          else if (changeSyncDTO.getVersionableName().equals(changeSelectorPath.lastSegment())) {
            matchedChanges.add(changeSyncDTO);
          }
        }
      }
      


      if (matchedChanges.size() == 0)
        throw StatusHelper.itemNotFound(NLS.bind(Messages.ListStatesCmd_CHANGE_NOT_FOUND, changeSelector));
      if (matchedChanges.size() > 1) {
        IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
        err.println(Messages.ListStatesCmd_MULTIPLE_CHANGE_MATCHED);
        for (ChangeSyncDTO change : matchedChanges) {
          err.indent().println(AliasUtil.selector(change.getPathHint(), 
            UUID.valueOf(change.getVersionableItemId()), repoUri, RepoUtil.ItemType.VERSIONABLE));
        }
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.Common_AMBIGUOUS_CHANGE, changeSelector));
      }
      
      changes.add((ChangeSyncDTO)matchedChanges.get(0));
    }
    
    return changes;
  }
  
  private void listStates(ParmsWorkspace ws, ChangeSetSyncDTO csDTO, List<ChangeSyncDTO> changes, ITeamRepository repo) throws FileSystemException
  {
    IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRichClientRestService.class);
    
    IScmRichClientRestService.ParmsGetIntermediateHistory parms = new IScmRichClientRestService.ParmsGetIntermediateHistory();
    changeSetItemId = csDTO.getChangeSetItemId();
    
    Map<ChangeSyncDTO, List<ScmIntermediateChangeNode>> changeToStates = new HashMap();
    for (ChangeSyncDTO change : changes) {
      ScmIntermediateHistory result = null;
      
      if (change.getVersionableItemType().equals("file")) {
        versionableItemId = change.getVersionableItemId();
        versionableItemNamespace = IFileItem.ITEM_TYPE.getNamespaceURI();
        versionableItemType = IFileItem.ITEM_TYPE.getName();
        try {
          result = scmService.getChangeSetHistory(parms);
        } catch (TeamRepositoryException e) {
          throw StatusHelper.wrap(Messages.ListStatesCmd_FAILURE, e, 
            new IndentingPrintStream(config.getContext().stderr()));
        }
      }
      
      changeToStates.put(change, result != null ? result.getHistory() : null);
    }
    

    JSONObject changesObj = new JSONObject();
    JSONArray changeArray = jsonizeStates(changeToStates, csDTO.isIsActive(), repo, config);
    changesObj.put("changes", changeArray);
    

    if (config.isJSONEnabled()) {
      if (changeArray.size() > 0) {
        config.getContext().stdout().print(changesObj);
      }
    } else {
      IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
      printStates(changeArray, config, out);
    }
  }
  
  private JSONArray jsonizeStates(Map<ChangeSyncDTO, List<ScmIntermediateChangeNode>> changeToStates, boolean isCsActive, ITeamRepository repo, IClientConfiguration config) throws FileSystemException
  {
    JSONArray changeArray = new JSONArray();
    
    SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy hh:mm a");
    
    for (Map.Entry<ChangeSyncDTO, List<ScmIntermediateChangeNode>> entry : changeToStates.entrySet())
    {
      ChangeSyncDTO change = (ChangeSyncDTO)entry.getKey();
      String changePath = "/" + change.getPathHint();
      
      JSONObject changeObj = new JSONObject();
      changeObj.put("uuid", change.getVersionableItemId());
      changeObj.put("url", repo.getRepositoryURI());
      changeObj.put("path", changePath);
      

      JSONArray statesArray = new JSONArray();
      changeObj.put("states", statesArray);
      

      if (entry.getValue() != null)
      {

        Map<String, ContentStatusDTO> stateToStatusMap = getStateStatus(((ChangeSyncDTO)entry.getKey()).getVersionableItemId(), 
          (List)entry.getValue(), repo, config);
        
        for (ScmIntermediateChangeNode intermediateChange : (List)entry.getValue()) {
          boolean isLastChange = false;
          if ((!intermediateChange.isSetSuccessors()) || (intermediateChange.getSuccessors().size() == 0)) {
            isLastChange = true;
          }
          
          JSONObject chgStateObj = jsonizeState(intermediateChange.getType(), isCsActive, isLastChange, 
            (ContentStatusDTO)stateToStatusMap.get(intermediateChange.getStateId()), repo);
          String date = sdf.format(intermediateChange.getDate());
          
          JSONObject stateObj = new JSONObject();
          stateObj.put("uuid", intermediateChange.getStateId());
          stateObj.put("url", repo.getRepositoryURI());
          stateObj.put("state", chgStateObj);
          stateObj.put("date", date);
          
          statesArray.add(stateObj);
        }
      }
      
      changeArray.add(changeObj);
    }
    
    return changeArray;
  }
  
  private Map<String, ContentStatusDTO> getStateStatus(String itemId, List<ScmIntermediateChangeNode> stateNodes, ITeamRepository repo, IClientConfiguration config) throws FileSystemException
  {
    List<String> fileItemIdList = new ArrayList();
    List<String> fileStateIdList = new ArrayList();
    
    for (ScmIntermediateChangeNode stateNode : stateNodes) {
      if (stateNode.getStateId() != null) {
        fileItemIdList.add(itemId);
        fileStateIdList.add(stateNode.getStateId());
      }
    }
    
    IFilesystemRichClientRestService.ParmsContentStatus parms = new IFilesystemRichClientRestService.ParmsContentStatus();
    fileItemId = ((String[])fileItemIdList.toArray(new String[fileItemIdList.size()]));
    fileItemStateId = ((String[])fileStateIdList.toArray(new String[fileStateIdList.size()]));
    
    IFilesystemRichClientRestService service = (IFilesystemRichClientRestService)((IClientLibraryContext)repo)
      .getServiceInterface(IFilesystemRichClientRestService.class);
    
    try
    {
      result = service.getContentStatus(parms);
    } catch (TeamRepositoryException e) { ContentStatusDTO[] result;
      throw StatusHelper.wrap(Messages.ListStatesCmd_FAILURE_FETCHING_STATUS, e, 
        new IndentingPrintStream(config.getContext().stderr()));
    }
    ContentStatusDTO[] result;
    Map<String, ContentStatusDTO> stateToDeletedMap = new HashMap();
    int count = 0;
    for (String stateId : fileStateIdList) {
      stateToDeletedMap.put(stateId, result[(count++)]);
    }
    
    return stateToDeletedMap;
  }
  
  private void printStates(JSONArray changeArray, IClientConfiguration config, IndentingPrintStream out) {
    if (changeArray.size() == 0) {
      out.println(Messages.ListStatesCmd_NO_CHANGES_FOUND); return;
    }
    
    Iterator localIterator2;
    for (Iterator localIterator1 = changeArray.iterator(); localIterator1.hasNext(); 
        






        localIterator2.hasNext())
    {
      Object changeObj = localIterator1.next();
      JSONObject change = (JSONObject)changeObj;
      
      String changeAlias = AliasUtil.alias((String)change.get("uuid"), 
        (String)change.get("url"), RepoUtil.ItemType.VERSIONABLE);
      out.println(NLS.bind(Messages.ListCmd_Workspace_Header, changeAlias, (String)change.get("path")));
      
      JSONArray stateArray = (JSONArray)change.get("states");
      localIterator2 = stateArray.iterator(); continue;Object stateObj = localIterator2.next();
      JSONObject state = (JSONObject)stateObj;
      

      String stateAlias = "(----)";
      if (state.get("uuid") != null) {
        stateAlias = AliasUtil.alias((String)state.get("uuid"), 
          (String)state.get("url"), RepoUtil.ItemType.VERSIONABLE);
      }
      
      String stateString = getStateString((JSONObject)state.get("state"));
      out.indent().println(NLS.bind(Messages.Common_PRINT_3_ITEMS, new Object[] { stateAlias, 
        stateString, (String)state.get("date") }));
    }
  }
  
  private JSONObject jsonizeState(int type, boolean isActive, boolean isLastChange, ContentStatusDTO status, ITeamRepository repo)
    throws FileSystemException
  {
    JSONObject chgStateObj = new JSONObject();
    
    if (type == ScmIntermediateChangeNode.TYPE_BEFORE) {
      chgStateObj.put("type", Messages.ListStatesCmd_STATE_INITIAL);
    } else if (type == ScmIntermediateChangeNode.TYPE_AFTER) {
      chgStateObj.put("type", Messages.ListStatesCmd_STATE_MODIFIED);
    } else if (type == ScmIntermediateChangeNode.TYPE_MERGE) {
      chgStateObj.put("type", Messages.ListStatesCmd_STATE_PROPOSED);
    } else if (type == ScmIntermediateChangeNode.TYPE_ADDITION) {
      chgStateObj.put("type", Messages.ListStatesCmd_STATE_ADDED);
    } else if (type == ScmIntermediateChangeNode.TYPE_DELETE) {
      chgStateObj.put("type", Messages.ListStatesCmd_STATE_DELETED);
    } else if (type == ScmIntermediateChangeNode.TYPE_MOVE) {
      chgStateObj.put("type", Messages.ListStatesCmd_STATE_MOVED);
    } else if (type == ScmIntermediateChangeNode.TYPE_RENAME) {
      chgStateObj.put("type", Messages.ListStatesCmd_STATE_RENAMED);
    } else if (type == ScmIntermediateChangeNode.TYPE_UNDO) {
      chgStateObj.put("type", Messages.ListStatesCmd_STATE_UNDO);
    }
    
    if (isLastChange) {
      if (isActive) {
        chgStateObj.put("status", Messages.ListStatesCmd_STATE_CURRENT);
      } else {
        chgStateObj.put("status", Messages.ListStatesCmd_STATE_FINAL);
      }
    }
    
    if ((status != null) && (status.isSetStatus())) {
      chgStateObj.put("state-content", status.getStatus());
      
      String contrib = "";
      if (status.getDeletedByContributorId() != null) {
        IContributor contributor = (IContributor)RepoUtil.getItem(IContributor.ITEM_TYPE, 
          UUID.valueOf(status.getDeletedByContributorId()), repo, config);
        contrib = contributor.getUserId();
      }
      chgStateObj.put("deletedby-contributor", contrib);
      
      String date = "";
      if (status.getDeletedOn() != 0L) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy hh:mm a");
        date = sdf.format(new Date(status.getDeletedOn()));
      }
      chgStateObj.put("deleted-on", date);
    }
    
    return chgStateObj;
  }
  
  private String getStateString(JSONObject stateObj) {
    String stateString = "";
    
    stateString = (String)stateObj.get("type");
    
    if (stateObj.get("status") != null) {
      stateString = NLS.bind(Messages.PropertyListCmd_KeyValue, (String)stateObj.get("status"), 
        stateString);
    }
    
    String contentStatus = (String)stateObj.get("state-content");
    if ((contentStatus != null) && (!contentStatus.equalsIgnoreCase(IVersionedContentService.ContentStatus.PRESENT.name()))) {
      String statusMsg = contentStatus;
      if (contentStatus.equalsIgnoreCase(IVersionedContentService.ContentStatus.DELETED.name())) {
        String contrib = Messages.LockListCmd_Unknown;
        if (stateObj.get("deletedby-contributor") != null) {
          contrib = (String)stateObj.get("deletedby-contributor");
        }
        
        String date = Messages.LockListCmd_Unknown;
        if (stateObj.get("deleted-on") != null) {
          date = (String)stateObj.get("deleted-on");
        }
        
        statusMsg = NLS.bind(Messages.ListStatesCmd_DELETED, contrib, date);
      }
      
      stateString = NLS.bind(Messages.ListStatesCmd_STATUS, stateString, statusMsg);
    }
    
    return stateString;
  }
}
