package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;











public class MoveInRepositoryCmdOpts
  implements IOptionSource
{
  public static final NamedOptionDefinition OPT_SOURCE_CS_COMMENT = new NamedOptionDefinition(
    null, "source-cs-comment", 1);
  
  public static final NamedOptionDefinition OPT_DESTINATION_CS_COMMENT = new NamedOptionDefinition(
    null, "destination-cs-comment", 1);
  


  public static final NamedOptionDefinition OPT_COMPONENT_SELECTOR = new NamedOptionDefinition("D", "destination-component", 1);
  
  public MoveInRepositoryCmdOpts() {}
  
  public static final NamedOptionDefinition OPT_SOURCE_COMPONENT_SELECTOR = new NamedOptionDefinition("S", "source-component", 1);
  
  public static final IOptionKey OPT_SOURCE_KEY = new OptionKey("source", "@");
  
  public static final IOptionKey OPT_TARGET_KEY = new OptionKey("destination", "@");
  

  public static final PositionalOptionDefinition OPT_SOURCE = new PositionalOptionDefinition(OPT_SOURCE_KEY, "source", 1, 1, "@");
  

  public static final PositionalOptionDefinition OPT_TARGET_PATH = new PositionalOptionDefinition(OPT_TARGET_KEY, "destination", 1, 1, "@");
  
  public static final INamedOptionDefinition OPT_WORKSPACE_NAME = new NamedOptionDefinition(
    "w", "workspace", 1, "@");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    options.setLongHelp(Messages.MoveInRepositoryCmdOpts_LongHelp);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED, Messages.Common_FORCE_OVERWRITE_UNCOMMITTED)
      .addOption(OPT_SOURCE_CS_COMMENT, Messages.MoveInRepositoryCmdOpts_SOURCE_CS_COMMENT)
      .addOption(OPT_DESTINATION_CS_COMMENT, Messages.MoveInRepositoryCmdOpts_DESTINATION_CS_COMMENT)
      .addOption(OPT_COMPONENT_SELECTOR, Messages.MoveInRepositoryCmdOpts_TARGET_COMPONENT_SELECTOR)
      .addOption(OPT_WORKSPACE_NAME, Messages.MoveInRepositoryCmdOpts_WORKSPACE_NAME)
      .addOption(OPT_SOURCE_COMPONENT_SELECTOR, Messages.MoveInRepositoryCmdOpts_SOURCE_COMPONENT_SELECTOR, true)
      .addOption(OPT_SOURCE, Messages.MoveInRepositoryCmdOpts_SOURCE)
      .addOption(OPT_TARGET_PATH, Messages.MoveInRepositoryCmdOpts_TARGET_PATH);
    
    return options;
  }
}
