package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;











public class SandboxStructureCmdOpts
  implements IOptionSource
{
  public static final NamedOptionDefinition OPT_COMPONENT = new NamedOptionDefinition("C", "component", 1);
  public static final NamedOptionDefinition OPT_WORKSPACE = new NamedOptionDefinition("w", "workspace", 1);
  
  public SandboxStructureCmdOpts() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    SubcommandUtil.addCredentialsToOptions(options);
    
    options
      .addOption(new ContinuousGroup()
      .addOption(OPT_WORKSPACE, Messages.SandboxStructureCmdOptions_WORKSPACE_HELP, false)
      .addOption(OPT_COMPONENT, Messages.SandboxStructureCmdOptions_COMPONENT_HELP, false))
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP);
    
    return options;
  }
}
