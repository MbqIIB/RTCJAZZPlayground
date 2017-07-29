package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import org.eclipse.osgi.util.NLS;





public class WorkspacePropertyGetCmd
  extends WorkspacePropertyListCmd
  implements IOptionSource
{
  public WorkspacePropertyGetCmd() {}
  
  public static final PositionalOptionDefinition OPT_PROPERTY_NAME = new PositionalOptionDefinition("property-name", 1, 1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(OPT_PROPERTY_NAME, NLS.bind(Messages.WorkspacePropertiesCmdOptions_OPT_PROPERTY_GET_NAME_HELP, 
      new String[] { "name", "ownedby", "owned", "visibility", "visi", 
      "description", "desc", "auto-lock-files" }))
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, -1, "@"), 
      Messages.WorkspacePropertiesCmdOptions_GET_WORKSPACE_HELP);
    
    return options;
  }
  
  public void run() throws FileSystemException {
    config = config;
    
    ICommandLine cli = config.getSubcommandCommandLine();
    String propertyName = cli.getOption(OPT_PROPERTY_NAME, null);
    
    initializeArgs(propertyName);
    listProperties(propertyName);
  }
}
