package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import java.util.Arrays;
import java.util.List;
import org.eclipse.osgi.util.NLS;







public class WorkspacePropertyCmd
  extends AbstractSubcommand
{
  public WorkspacePropertyCmd() {}
  
  IFilesystemRestClient client = null;
  List<IScmCommandLineArgument> wsSelectorList = null;
  
  public static final String NAME_PROPERTY = "name";
  public static final String OWNEDBY_PROPERTY = "ownedby";
  public static final String OWNEDBY_ALIAS_PROPERTY = "owned";
  public static final String VISIBILITY_PROPERTY = "visibility";
  public static final String VISIBILITY_ALIAS_PROPERTY = "visi";
  public static final String DESCRIPTION_PROPERTY = "description";
  public static final String DESCRIPTION_ALIAS_PROPERTY = "desc";
  public static final String TEAMAREA_VISIBILITY = "teamarea";
  public static final String PROJECTAREA_VISIBILITY = "projectarea";
  public static final String ACCESSGROUP_VISIBILITY = "accessgroup";
  public static final String PUBLIC_VISIBILITY = "public";
  public static final String PRIVATE_VISIBILITY = "private";
  public static final String AUTO_FILE_LOCK_PATTTERN_PROPERTY = "auto-lock-files";
  public static final String UNKNOWN = "unknown";
  
  public static final String[] PROPERTIES = { "name", "ownedby", "owned", "visibility", "visi", "description", "desc", "auto-lock-files" };
  
  protected void initializeArgs(String propertyName) throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    wsSelectorList = ScmCommandLineArgument.createList(cli.getOptionValues(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(wsSelectorList, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    if ((propertyName != null) && (!Arrays.asList(PROPERTIES).contains(propertyName))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_PROPERTY_NAME, propertyName));
    }
    

    client = SubcommandUtil.setupDaemon(config);
  }
  
  protected static boolean hasAllWorkspaceProperties(JSONArray jWsArray) {
    for (Object obj : jWsArray) {
      JSONObject jWs = (JSONObject)obj;
      
      Long statusCode = (Long)jWs.get("status-code");
      if (statusCode.longValue() != 0L) {
        return false;
      }
    }
    
    return true;
  }
  
  public void run()
    throws FileSystemException
  {}
}
