package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.process.common.IDescription;
import com.ibm.team.process.common.IDevelopmentLine;
import com.ibm.team.process.common.IDevelopmentLineHandle;
import com.ibm.team.process.common.IIteration;
import com.ibm.team.repository.client.IItemManager;
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
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.osgi.util.NLS;








public class SnapshotPropertyListCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  IFilesystemRestClient client;
  public static final String NAME_PROPERTY = "name";
  public static final String OWNEDBY_PROPERTY = "ownedby";
  public static final String OWNEDBY_ALIAS_PROPERTY = "owned";
  public static final String DESCRIPTION_PROPERTY = "description";
  public static final String DESCRIPTION_ALIAS_PROPERTY = "desc";
  public static final String ITERATION_PROPERTY = "iteration";
  public static final String RELEASE_PROPERTY = "release";
  public static final String[] PROPERTIES = { "name", "ownedby", "owned", "description", "desc", "iteration", "release" };
  
  public static final PositionalOptionDefinition OPT_SNAPSHOT_SELECTOR = new PositionalOptionDefinition("snapshot", 1, 1, "@");
  public static final NamedOptionDefinition OPT_WORKSPACE_SELECTOR = new NamedOptionDefinition("w", "workspace", 1);
  
  public SnapshotPropertyListCmd() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(OPT_SNAPSHOT_SELECTOR, Messages.SnapshotPropertiesCmdOptions_SNAPSHOT_LIST_HELP)
      .addOption(OPT_WORKSPACE_SELECTOR, Messages.SnapshotPropertiesCmdOptions_WORKSPACE_HELP)
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT);
    
    return options;
  }
  
  public void run() throws FileSystemException
  {
    listProperties(null);
  }
  
  protected void listProperties(String propertyName) throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    if ((propertyName != null) && (!Arrays.asList(PROPERTIES).contains(propertyName))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_PROPERTY_NAME, propertyName));
    }
    

    client = SubcommandUtil.setupDaemon(config);
    
    IScmCommandLineArgument ssSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_SNAPSHOT_SELECTOR), config);
    SubcommandUtil.validateArgument(ssSelector, RepoUtil.ItemType.SNAPSHOT);
    
    IScmCommandLineArgument wsSelector = null;
    if (cli.hasOption(OPT_WORKSPACE_SELECTOR)) {
      wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_WORKSPACE_SELECTOR), config);
      SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    }
    
    List<String> propertyNames = null;
    if (propertyName != null) {
      propertyNames = new ArrayList(1);
      propertyNames.add(propertyName.toLowerCase());
    }
    
    listProperties(ssSelector, wsSelector, propertyNames, propertyName == null, client, config);
  }
  


  public static void listProperties(IScmCommandLineArgument ssSelector, IScmCommandLineArgument wsSelector, List<String> propertyNames, boolean printCaption, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, ssSelector);
    

    IWorkspace ws = null;
    if (wsSelector != null) {
      ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    }
    

    IBaselineSet ss = RepoUtil.getSnapshot(ws != null ? ws.getItemId().getUuidValue() : null, ssSelector.getItemSelector(), repo, config);
    

    JSONObject jSs = new JSONObject();
    JSONObject jProps = getProperties(ss, propertyNames, repo, config);
    jSs.put("snapshot", ss.getName());
    jSs.put("properties", jProps);
    

    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jSs);
    } else {
      printProperties(jSs, printCaption, config);
    }
  }
  
  private static JSONObject getProperties(IBaselineSet ss, List<String> propertyNames, ITeamRepository repo, IScmClientConfiguration config)
    throws FileSystemException
  {
    JSONObject jProps = new JSONObject();
    new IndentingPrintStream(config.getContext().stdout());
    

    if ((propertyNames == null) || (propertyNames.contains("name"))) {
      jProps.put("name", ss.getName());
    }
    

    if ((propertyNames == null) || (propertyNames.contains("description")) || (propertyNames.contains("desc"))) {
      jProps.put("description", ss.getComment());
    }
    

    if ((propertyNames == null) || (propertyNames.contains("ownedby")) || (propertyNames.contains("owned"))) {
      IWorkspaceHandle wh = ss.getOwner();
      if (wh != null) {
        IWorkspace ws = RepoUtil.getWorkspace(wh.getItemId().getUuidValue(), true, true, repo, config);
        if (ws != null) {
          jProps.put("ownedby", ws.getName());
          jProps.put("ownedby_id", ws.getItemId().getUuidValue());
        }
      }
    }
    

    jProps.put("uuid", ss.getItemId().getUuidValue());
    

    jProps.put("url", repo.getRepositoryURI());
    

    if ((propertyNames == null) || (propertyNames.contains("iteration"))) {
      JSONArray iteration = getIteration(ss, repo, config);
      if (iteration != null) {
        jProps.put("iteration", iteration);
      }
    }
    
    return jProps;
  }
  
  private static JSONArray getIteration(IBaselineSet ss, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException {
    List<IIteration> fetched = RepoUtil.getBaselineSetIterations(ss, repo, config);
    JSONArray array = new JSONArray();
    for (IIteration iteration : fetched) {
      if (iteration != null) {
        JSONObject properties = new JSONObject();
        properties.put("uuid", iteration.getItemId().getUuidValue());
        properties.put("id", iteration.getId());
        properties.put("name", iteration.getName());
        properties.put("label", iteration.getLabel());
        properties.put("description", iteration.getDescription().getSummary());
        properties.put("archived", Boolean.valueOf(iteration.isArchived()));
        try
        {
          String parentId = "";
          IDevelopmentLineHandle developmentLineHandle = iteration.getDevelopmentLine();
          IIteration loopIteration = iteration;
          
          while ((loopIteration != null) && (loopIteration.getParent() != null)) {
            IIteration parentIteration = (IIteration)repo.itemManager().fetchCompleteItem(loopIteration.getParent(), 0, null);
            if (parentIteration != null) {
              if (parentId.length() > 0) {
                parentId = parentIteration.getLabel() + '/' + parentId;
              } else {
                parentId = parentIteration.getLabel();
              }
            }
            loopIteration = parentIteration;
          }
          
          IDevelopmentLine line = (IDevelopmentLine)repo.itemManager().fetchCompleteItem(developmentLineHandle, 0, null);
          if (line != null) {
            if (parentId.length() > 0) {
              parentId = line.getLabel() + '/' + parentId;
            } else {
              parentId = line.getLabel();
            }
          }
          
          if (!parentId.isEmpty()) {
            properties.put("iteration_parent", parentId);
          }
        }
        catch (TeamRepositoryException localTeamRepositoryException) {}
        
        array.add(properties);
      }
    }
    if (array.size() > 0) {
      return array;
    }
    return null;
  }
  
  private static void printProperties(JSONObject jSs, boolean printCaption, IScmClientConfiguration config) throws FileSystemException
  {
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    

    JSONObject jProps = (JSONObject)jSs.get("properties");
    
    String itemId = (String)jProps.get("uuid");
    String repoUri = (String)jProps.get("url");
    String ownedby_id = (String)jProps.get("ownedby_id");
    

    String name = (String)jProps.get("name");
    if (name != null) {
      name = AliasUtil.selector(name, UUID.valueOf(itemId), repoUri, RepoUtil.ItemType.SNAPSHOT);
      out.println(printCaption ? NLS.bind(Messages.WorkspacePropertiesCmd_NAME, name) : name);
    }
    

    String description = (String)jProps.get("description");
    if ((description != null) && (!description.isEmpty())) {
      if (printCaption) {
        out.println(Messages.WorkspacePropertiesCmd_DESCRIPTION);
      }
      out.println(description);
    }
    

    Object obj = jProps.get("ownedby");
    if (obj != null) {
      String ownedby = (String)obj;
      if ((ownedby != null) && (!ownedby.isEmpty())) {
        ownedby = AliasUtil.selector(ownedby, UUID.valueOf(ownedby_id), repoUri, RepoUtil.ItemType.STREAM);
        out.println(printCaption ? NLS.bind(Messages.WorkspacePropertiesCmd_OWNEDBY, ownedby) : ownedby);
      }
    }
    

    JSONArray jIteration = (JSONArray)jProps.get("iteration");
    if (jIteration != null) {
      if (printCaption) {
        out.println(Messages.SnapshotPropertiesCmd_ITERATIONS);
      }
      for (Object entry : jIteration) {
        JSONObject prop = (JSONObject)entry;
        String timeline = (String)prop.get("iteration_parent");
        String iteration = (String)prop.get("id");
        Boolean archived = (Boolean)prop.get("archived");
        String output = iteration;
        if ((timeline != null) && (!timeline.equals(""))) {
          output = timeline + "/" + output;
        }
        if (archived.booleanValue()) {
          output = output + " (archived)";
        }
        out.indent().println(output);
      }
    }
  }
}
