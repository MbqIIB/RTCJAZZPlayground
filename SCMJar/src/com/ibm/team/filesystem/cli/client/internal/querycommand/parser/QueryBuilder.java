package com.ibm.team.filesystem.cli.client.internal.querycommand.parser;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.scm.common.IGenericQueryNode;
import com.ibm.team.scm.common.IGenericQueryNode.Factory;
import com.ibm.team.scm.common.dto.QueryOperations;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryBaseListener;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryLexer;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.Alias_valueContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.Arg_listContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.Arg_valueContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.ArgumentContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.Attribute_containsContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.Attribute_equalsContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.Attribute_matchesContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.Attribute_nameContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.ClauseContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.ClauseExprContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.ConjunctionExprContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.Equality_valueContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.FunctionContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.Integer_valueContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.ParenExprContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.QueryContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.String_valueContext;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.Uuid_valueContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;






public final class QueryBuilder
  extends QueryBaseListener
{
  private final Parser parser;
  private final ITeamRepository repo;
  
  public static IGenericQueryNode createScmQuery(String queryText, ITeamRepository repo)
  {
    CharStream stream = new ANTLRInputStream(queryText);
    TokenSource lexer = new QueryLexer(stream);
    TokenStream tokens = new CommonTokenStream(lexer);
    QueryParser parser = new QueryParser(tokens);
    QueryParser.QueryContext queryParseTree = parser.query();
    
    QueryBuilder builder = new QueryBuilder(parser, repo);
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(builder, queryParseTree);
    
    return (IGenericQueryNode)queries.get(queryParseTree.getChild(0));
  }
  




  private final Map<ParseTree, IGenericQueryNode> queries = new HashMap();
  
  public QueryBuilder(Parser parser, ITeamRepository repo) {
    this.parser = parser;
    this.repo = repo;
  }
  

  public void exitAttribute_equals(QueryParser.Attribute_equalsContext ctx)
  {
    TokenStream tokens = parser.getTokenStream();
    
    QueryParser.Equality_valueContext value = ctx.equality_value();
    
    String attributeName = getAttributeName(ctx.attribute_name());
    String stringLiteral = unquoteString(tokens.getText(value.getSourceInterval()));
    
    IGenericQueryNode query = IGenericQueryNode.FACTORY.newInstance(
      QueryOperations.OPERATION_EQUALS);
    query.addChild(attributeName);
    query.addChild(stringLiteral);
    
    queries.put(ctx, query);
  }
  


  public void exitAttribute_matches(QueryParser.Attribute_matchesContext ctx)
  {
    TokenStream tokens = parser.getTokenStream();
    
    QueryParser.String_valueContext value = ctx.string_value();
    
    String attributeName = getAttributeName(ctx.attribute_name());
    String stringLiteral = unquoteString(tokens.getText(value.getSourceInterval()));
    
    IGenericQueryNode query = IGenericQueryNode.FACTORY.newInstance(QueryOperations.OPERATION_MATCHES);
    
    query.addChild(attributeName);
    query.addChild(stringLiteral);
    
    queries.put(ctx, query);
  }
  

  public void exitAttribute_contains(QueryParser.Attribute_containsContext ctx)
  {
    TokenStream tokens = parser.getTokenStream();
    
    QueryParser.String_valueContext value = ctx.string_value();
    
    String attributeName = getAttributeName(ctx.attribute_name());
    String stringLiteral = unquoteString(tokens.getText(value.getSourceInterval()));
    
    IGenericQueryNode query = IGenericQueryNode.FACTORY.newInstance(QueryOperations.OPERATION_CONTAINS);
    
    query.addChild(attributeName);
    query.addChild(stringLiteral);
    
    queries.put(ctx, query);
  }
  

  public void exitClauseExpr(QueryParser.ClauseExprContext ctx)
  {
    queries.put(ctx, (IGenericQueryNode)queries.get(ctx.clause()));
  }
  
  public void exitConjunctionExpr(QueryParser.ConjunctionExprContext ctx)
  {
    ConjunctionType conjunction = ConjunctionType.getConjunctionType(ctx);
    IGenericQueryNode query = conjunction.create(
      (IGenericQueryNode)queries.get(ctx.expression().get(0)), 
      (IGenericQueryNode)queries.get(ctx.expression().get(1)));
    queries.put(ctx, query);
  }
  
  public void exitClause(QueryParser.ClauseContext ctx)
  {
    queries.put(ctx, getChildExpression(ctx));
  }
  
  public void exitFunction(QueryParser.FunctionContext ctx)
  {
    TokenStream tokens = parser.getTokenStream();
    
    String functionName = tokens.getText(ctx.IDENTIFIER().getSourceInterval());
    
    IGenericQueryNode query = IGenericQueryNode.FACTORY.newInstance(functionName);
    
    QueryParser.Arg_listContext argListCtx = ctx.arg_list();
    if (argListCtx != null) {
      for (QueryParser.ArgumentContext arg : argListCtx.argument()) {
        addFunctionArg(query, arg, functionName);
      }
    }
    
    queries.put(ctx, query);
  }
  
  public void exitParenExpr(QueryParser.ParenExprContext ctx)
  {
    queries.put(ctx, (IGenericQueryNode)queries.get(ctx.expression()));
  }
  
  private void addFunctionArg(IGenericQueryNode query, QueryParser.ArgumentContext arg, String functionName) {
    TokenStream tokens = parser.getTokenStream();
    
    QueryParser.Arg_valueContext argCtx = arg.arg_value();
    
    if (argCtx.equality_value() != null) {
      QueryParser.String_valueContext value = argCtx.equality_value().string_value();
      String stringLiteral = unquoteString(tokens.getText(value.getSourceInterval()));
      UUID itemUUID = QueryParserUtil.getUUIDForName(functionName, stringLiteral, repo);
      
      if (itemUUID != null) {
        query.addChild(itemUUID);
      } else {
        query.addChild(stringLiteral);
      }
    } else if (argCtx.uuid_value() != null) {
      QueryParser.Uuid_valueContext value = argCtx.uuid_value();
      String stringLiteral = unquoteString(tokens.getText(value.getSourceInterval()));
      
      query.addChild(UUID.valueOf(stringLiteral));
    } else if (argCtx.attribute_name() != null) {
      String stringLiteral = getAttributeName(argCtx.attribute_name());
      
      query.addChild(stringLiteral);
    } else if (argCtx.integer_value() != null) {
      QueryParser.Integer_valueContext value = argCtx.integer_value();
      Integer intValue = Integer.getInteger(tokens.getText(value.getSourceInterval()));
      
      query.addChild(intValue);
    } else if (argCtx.alias_value() != null) {
      QueryParser.Alias_valueContext value = argCtx.alias_value();
      String aliasString = tokens.getText(value.getSourceInterval());
      UUID itemUUID = QueryParserUtil.getUUIDForAlias(aliasString);
      
      query.addChild(itemUUID);
    }
    else
    {
      throw new RuntimeException(Messages.QueryBuilder_UnexpectedArgument);
    }
  }
  
  private IGenericQueryNode getChildExpression(ParserRuleContext context) {
    int numChildren = context.getChildCount();
    for (int i = 0; i < numChildren; i++) {
      ParseTree child = context.getChild(i);
      IGenericQueryNode childExpr = (child instanceof ParserRuleContext) ? 
        getChildExpression((ParserRuleContext)child) : 
        (IGenericQueryNode)queries.get(child);
      if (childExpr != null) {
        return childExpr;
      }
    }
    return (IGenericQueryNode)queries.get(context);
  }
  

  private String getAttributeName(QueryParser.Attribute_nameContext context)
  {
    TokenStream tokens = parser.getTokenStream();
    TerminalNode id = context.IDENTIFIER();
    TerminalNode literal = context.STRING_LITERAL();
    String name; if (id != null) {
      name = tokens.getText(id.getSourceInterval()); } else { String name;
      if (literal != null) {
        name = unquoteString(tokens.getText(literal.getSourceInterval()));
      }
      else
      {
        throw new RuntimeException(Messages.QueryBuilder_UnexpectedArgument); }
    }
    String name;
    return name;
  }
  
  private String unquoteString(String rawString) {
    StringBuilder bufString = new StringBuilder(rawString);
    if ((bufString.length() > 0) && (
      ((bufString.charAt(0) == '\'') && (bufString.charAt(bufString.length() - 1) == '\'')) || (
      (bufString.charAt(0) == '"') && (bufString.charAt(bufString.length() - 1) == '"'))))
    {
      bufString.deleteCharAt(bufString.length() - 1);
      
      bufString.deleteCharAt(0);
    }
    
    return bufString.toString();
  }
}
