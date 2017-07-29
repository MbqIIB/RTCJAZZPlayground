package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.IShare;
import com.ibm.team.filesystem.client.IShareable;
import com.ibm.team.filesystem.client.ISharingDescriptor;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IItem;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IContextHandle;
import com.ibm.team.scm.common.IFolderHandle;
import com.ibm.team.scm.common.internal.util.ItemId;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.osgi.util.NLS;

public class LastModCmd extends AbstractSubcommand
{
  private IFilesystemRestClient client;
  
  public LastModCmd() {}
  
  private DateFormat shortDateFormat = new SimpleDateFormat("yyyyMMddhhmm");
  
  public void run() throws FileSystemException {
    client = SubcommandUtil.setupDaemon(config);
    
    execute();
  }
  

  private List<ShareDTO> consumeArguments()
    throws FileSystemException
  {
    ICommandLine subargs = config.getSubcommandCommandLine();
    
    shortDateFormat = new SimpleDateFormat(subargs.getOption(LastModCmdOpts.OPT_DATE_FORMAT, SubcommandUtil.getDateFormat("yyyyMMddhhmm", config).toPattern()));
    List<String> paths = subargs.getOptions(LastModCmdOpts.OPT_TO_CALCULATE);
    
    return findFoldersInCFA(paths);
  }
  
  private List<ShareDTO> findFoldersInCFA(List<String> strPaths) throws FileSystemException
  {
    ILocation cfaRoot = new PathLocation(config.getContext().getCurrentWorkingDirectory());
    
    List<ILocation> paths = SubcommandUtil.makeAbsolutePaths(config, strPaths);
    if (paths.contains(cfaRoot))
    {
      List<ShareDTO> shareDTOList = RepoUtil.getSharesInSandbox(null, client, config);
      return shareDTOList;
    }
    

    for (ILocation path : paths) {
      if (!SubcommandUtil.exists(path, null)) {
        throw StatusHelper.inappropriateArgument(NLS.bind(
          Messages.Common_PATH_DOES_NOT_EXIST, path.toOSString()));
      }
    }
    
    List<ResourcePropertiesDTO> resPropList = RepoUtil.getResourceProperties(strPaths, 
      SubcommandUtil.shouldRefreshFileSystem(config), client, config, false);
    Object folders = new ArrayList(strPaths.size());
    for (ResourcePropertiesDTO resProp : resPropList) {
      if (!resProp.getVersionableItemType().equals("folder")) {
        throw StatusHelper.disallowed(NLS.bind(Messages.LastModCmd_5, resProp.getFullPath()));
      }
      if (resProp.getItemId() == null) {
        throw StatusHelper.inappropriateArgument(NLS.bind(
          Messages.Common_PATH_DOES_NOT_EXIST, resProp.getFullPath()));
      }
      ((List)folders).add(resProp.getShare());
    }
    
    return folders;
  }
  
  private Map<ItemId<IItem>, List<IShareable>> groupShareablesByConnection(List<IShareable> commitRoots) throws FileSystemException {
    HashMap<ItemId<IItem>, List<IShareable>> map = 
      new HashMap();
    
    for (IShareable shareable : commitRoots) {
      IShare share = shareable.getShare(null);
      
      IContextHandle handle = share.getSharingDescriptor().getConnectionHandle();
      
      ItemId<IItem> id = ItemId.create(handle);
      
      SubcommandUtil.addToMapOfLists(map, id, shareable);
    }
    
    return map;
  }
  
  private void execute() throws FileSystemException {
    org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot();
    

    List<ShareDTO> folders = consumeArguments();
    

    Map<ParmsWorkspace, List<ShareDTO>> groupShareByWs = new HashMap();
    for (ShareDTO shareDTO : folders) {
      ParmsWorkspace ws = new ParmsWorkspace(RepoUtil.getRepoUri(config, client, shareDTO), shareDTO.getContextItemId());
      List<ShareDTO> shareList = (List)groupShareByWs.get(ws);
      if (shareList == null) {
        shareList = new ArrayList();
        groupShareByWs.put(ws, shareList);
        
        RepoUtil.login(config, client, config.getConnectionInfo(repositoryUrl));
      }
      shareList.add(shareDTO);
    }
    
    if (config.isDryRun()) {
      return;
    }
    

    for (Map.Entry<ParmsWorkspace, List<ShareDTO>> entry : groupShareByWs.entrySet()) {
      commit((ParmsWorkspace)entry.getKey(), (List)entry.getValue());
    }
  }
  
  private void commit(ParmsWorkspace ws, List<ShareDTO> shareables) throws FileSystemException {
    ITeamRepository repo = RepoUtil.getSharedRepository(repositoryUrl, true);
    
    Map<ItemId<IComponent>, List<ShareDTO>> groupedByComponent = groupByComponent(shareables);
    
    for (Map.Entry<ItemId<IComponent>, List<ShareDTO>> entry : groupedByComponent.entrySet()) {
      IComponentHandle compHandle = (IComponentHandle)((ItemId)entry.getKey()).toHandle();
      

      List<IFolderHandle> folders = new ArrayList(((List)entry.getValue()).size());
      List<ShareDTO> shares = new ArrayList(((List)entry.getValue()).size());
      for (ShareDTO shareDTO : (List)entry.getValue()) {
        if (shareDTO.getRootVersionableItemType().equals("folder")) {
          com.ibm.team.scm.common.IVersionableHandle handle = RepoUtil.getVersionableHandle(repo, shareDTO.getRootVersionableItemId(), 
            null, shareDTO.getRootVersionableItemType(), config);
          folders.add((IFolderHandle)handle);
          shares.add(shareDTO);
        } else {
          throw StatusHelper.disallowed(NLS.bind(Messages.LastModCmd_5, 
            StringUtil.createPathString(shareDTO.getPath().getSegments())));
        }
      }
      
      try
      {
        List<IBaseline> baselines = ws.getWorkspaceConnection(null).getMostRecentBaselinesAffecting(compHandle, 
          folders, null);
        for (int i = 0; i < baselines.size(); i++) {
          IBaseline baseline = (IBaseline)baselines.get(i);
          ShareDTO shareable = (ShareDTO)shares.get(i);
          config.getContext().stdout().println(NLS.bind(Messages.LastModCmd_6, 
            StringUtil.createPathString(shareable.getPath().getSegments()), 
            shortDateFormat.format(baseline.getCreationDate())));
        }
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.LastModCmd_8, e, new IndentingPrintStream(config.getContext().stderr()), 
          repo.getRepositoryURI());
      }
    }
  }
  









  private Map<ItemId<IComponent>, List<ShareDTO>> groupByComponent(List<ShareDTO> shareables)
    throws FileSystemException
  {
    Map<ItemId<IComponent>, List<ShareDTO>> grouped = new HashMap();
    
    for (ShareDTO shareable : shareables) {
      ItemId<IComponent> id = new ItemId(IComponent.ITEM_TYPE, UUID.valueOf(shareable.getComponentItemId()));
      
      SubcommandUtil.addToMapOfLists(grouped, id, shareable);
    }
    
    return grouped;
  }
}
