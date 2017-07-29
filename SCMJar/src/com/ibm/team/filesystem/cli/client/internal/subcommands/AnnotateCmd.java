package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil.ChangeRequestInfo;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.common.internal.rest.IFilesystemRestService;
import com.ibm.team.filesystem.common.internal.rest.IFilesystemRestService.ParmsGetBlame;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.FilePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.WorkItemSyncDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.VersionedContentDeleted;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.osgi.util.NLS;










public class AnnotateCmd
  extends AbstractSubcommand
{
  private final int NUMBER_OF_PRINT_COLUMNS = 7;
  IFilesystemRestClient client = null;
  
  public AnnotateCmd() {}
  
  public void run() throws FileSystemException
  {
    ICommandLine subargs = config.getSubcommandCommandLine();
    String pathName = subargs.getOption(AnnotateCmdOptions.OPT_PATH);
    config.setEnableJSON(subargs.hasOption(CommonOptions.OPT_JSON));
    

    ILocation annotatePath = SubcommandUtil.makeAbsolutePath(config, pathName);
    
    if (!SubcommandUtil.exists(annotatePath, null)) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.AnnotateCmd_PathDoesNotExist, annotatePath.toOSString()));
    }
    
    client = SubcommandUtil.setupDaemon(config);
    ResourcePropertiesDTO resProps = RepoUtil.getResourceProperties(annotatePath.toOSString(), client, config);
    if (resProps.getItemId() == null) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.AnnotateCmd_NoRemotePath, annotatePath.toOSString()));
    }
    if (!resProps.getVersionableItemType().equals("file")) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.AnnotateCmd_MUST_BE_FILE, annotatePath.toOSString()));
    }
    
    ITeamRepository repo = RepoUtil.loginUrlArgAncOrOnPath(config, client, new PathLocation(annotatePath.toOSString()));
    

    String[] csIds = getChangeSetIds(repo, resProps);
    
    try
    {
      PrintAnnotations(repo, resProps, csIds);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(null, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
  }
  

  private String[] getChangeSetIds(ITeamRepository repo, ResourcePropertiesDTO resProps)
    throws FileSystemException
  {
    IFilesystemRestService service = (IFilesystemRestService)((IClientLibraryContext)repo)
      .getServiceInterface(IFilesystemRestService.class);
    
    IFilesystemRestService.ParmsGetBlame parms = new IFilesystemRestService.ParmsGetBlame();
    fileItemId = resProps.getItemId();
    workspaceItemId = resProps.getShare().getContextItemId();
    componentItemId = resProps.getShare().getComponentItemId();
    
    try
    {
      result = service.getBlame(parms);
    } catch (TeamRepositoryException e) { String[] result;
      if ((e instanceof VersionedContentDeleted)) {
        throw StatusHelper.versionedContentDeleted(NLS.bind(Messages.DiffCmd_DELETED_CONTENT, StringUtil.createPathString(resProps.getPath().getRelativePath().getSegments())));
      }
      throw StatusHelper.wrap(Messages.AnnotateCmd_ServiceError, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    String[] result;
    return result;
  }
  









  private void PrintAnnotations(ITeamRepository repo, ResourcePropertiesDTO resProps, String[] csIds)
    throws FileSystemException, TeamRepositoryException
  {
    JSONObject jResult = jsonizeAnnotations(repo, resProps, csIds);
    
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jResult);
      return;
    }
    


    List<String> strings = new ArrayList(csIds.length * 7);
    
    assert (jResult != null);
    
    JSONArray jAnnotations = (JSONArray)jResult.get("annotations");
    
    for (Object obj : jAnnotations) {
      JSONObject jAnnotation = (JSONObject)obj;
      
      strings.add((String)jAnnotation.get("line-no"));
      strings.add((String)jAnnotation.get("author"));
      

      String alias = AliasUtil.alias((String)jAnnotation.get("uuid"), repo.getRepositoryURI(), RepoUtil.ItemType.CHANGESET);
      if (alias == null) {
        alias = "";
      }
      
      strings.add(alias);
      strings.add((String)jAnnotation.get("modified"));
      strings.add((String)jAnnotation.get("workitem"));
      strings.add((String)jAnnotation.get("comment"));
      strings.add((String)jAnnotation.get("line"));
    }
    

    IndentingPrintStream ps = new IndentingPrintStream(config.getContext().stdout());
    StringUtil.printTable(ps, 7, true, (CharSequence[])strings.toArray(new String[strings.size()]));
  }
  

  private JSONObject jsonizeAnnotations(ITeamRepository repo, ResourcePropertiesDTO resProps, String[] csIds)
    throws FileSystemException, TeamRepositoryException
  {
    ChangeSetSyncDTO[] csDTOList = RepoUtil.findChangeSets(Arrays.asList(csIds), false, null, null, 
      repo.getRepositoryURI(), client, config);
    

    List<ChangeSetSyncDTO> csList = new ArrayList(csIds.length);
    for (String csId : csIds) {
      for (ChangeSetSyncDTO csDTO : csDTOList) {
        if (csId.equals(csDTO.getChangeSetItemId())) {
          csList.add(csDTO);
          break;
        }
      }
    }
    

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ByteArrayInputStream stream = null;
    BufferedReader reader = null;
    JSONObject jResult = new JSONObject();
    try
    {
      RepoUtil.httpDownloadFile(repo, resProps.getShare().getContextItemId(), 
        resProps.getShare().getComponentItemId(), resProps.getItemId(), 
        resProps.getStateId(), bos, config);
      
      stream = new ByteArrayInputStream(bos.toByteArray());
      String line = null;
      int count = 0;
      
      JSONArray jAnnotations = new JSONArray();
      jResult.put("annotations", jAnnotations);
      
      String encoding = null;
      FilePropertiesDTO fileProps = resProps.getFileProperties();
      if (fileProps != null) {
        encoding = fileProps.getEncoding();
      }
      
      reader = new BufferedReader(new InputStreamReader(stream, encoding));
      
      while ((line = reader.readLine()) != null) {
        assert (count <= csIds.length);
        
        JSONObject jAnnotation = new JSONObject();
        
        ChangeSetSyncDTO cs = (ChangeSetSyncDTO)csList.get(count);
        

        String wiString = getWorkItemIds(cs);
        SimpleDateFormat df = SubcommandUtil.getDateFormat("yyyy-MM-dd hh:mm a", config);
        
        jAnnotation.put("line-no", Integer.toString(count + 1));
        jAnnotation.put("author", cs.getAuthorContributorName());
        jAnnotation.put("uuid", cs.getChangeSetItemId());
        jAnnotation.put("modified", df.format(Long.valueOf(cs.getLastChangeDate())));
        jAnnotation.put("workitem", wiString);
        jAnnotation.put("comment", cs.getChangeSetComment().substring(0, 
          cs.getChangeSetComment().length() > 20 ? 20 : cs.getChangeSetComment().length()));
        jAnnotation.put("line", line);
        
        count++;
        jAnnotations.add(jAnnotation);
      }
    }
    catch (IOException e) {
      throw StatusHelper.failure(Messages.AnnotateCmd_IOReadError, e);
    }
    finally {
      if (bos != null) {
        try {
          bos.close();
        }
        catch (IOException localIOException1) {}
      }
      if (reader != null) {
        try {
          reader.close();
        }
        catch (IOException localIOException2) {}
      }
      if (stream != null) {
        try {
          stream.close();
        }
        catch (IOException localIOException3) {}
      }
    }
    
    return jResult;
  }
  
  private String getWorkItemIds(ChangeSetSyncDTO cs)
  {
    StringBuffer wiString = new StringBuffer();
    
    if (cs.getWorkItems().size() > 0)
    {
      List<WorkItemSyncDTO> wiDTOList = cs.getWorkItems();
      
      int count = 0;
      int[] wiIds = new int[wiDTOList.size()];
      for (WorkItemSyncDTO wiDTO : wiDTOList) {
        JSONPrintUtil.ChangeRequestInfo crInfo = new JSONPrintUtil.ChangeRequestInfo(wiDTO.getLabel());
        wiIds[(count++)] = crInfo.getId();
      }
      

      Arrays.sort(wiIds);
      
      for (int index = 0; index < wiIds.length; index++) {
        if (wiString.length() > 0) {
          wiString.append(",");
        }
        wiString.append(wiIds[index]);
      }
    }
    
    return wiString.toString();
  }
}
