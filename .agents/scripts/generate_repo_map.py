#!/usr/bin/env python3
import os
import sys
import time
from pathlib import Path

try:
    import tree_sitter_java as tsjava
    from tree_sitter import Language, Parser
except ImportError:
    print("Error: tree_sitter and tree_sitter_java are required.")
    print("Run: pip install tree-sitter tree-sitter-java")
    sys.exit(1)

JAVA_LANGUAGE = Language(tsjava.language())
parser = Parser(JAVA_LANGUAGE)

def get_text(node, source_bytes):
    return source_bytes[node.start_byte:node.end_byte].decode("utf-8", errors="replace").strip()

def clean_signature(parts):
    # Join and normalize spaces
    sig = " ".join(" ".join(parts).split())
    # Clean space before parentheses
    sig = sig.replace(" (", "(").replace("( ", "(")
    return sig

def extract_signatures(node, source_bytes, indent=1):
    lines = []
    pad = "  " * indent
    type_name = node.type
    
    if type_name in ("class_declaration", "interface_declaration", "record_declaration", "enum_declaration"):
        header_parts = []
        for child in node.children:
            if child.type in ("class_body", "interface_body", "record_body", "enum_body"):
                break
            header_parts.append(get_text(child, source_bytes))
        
        header = clean_signature(header_parts)
        lines.append(f"{pad}{header} {{")
        
        body = None
        for child in node.children:
            if child.type in ("class_body", "interface_body", "record_body", "enum_body"):
                body = child
                break
        
        if body:
            for item in body.children:
                sub_lines = extract_signatures(item, source_bytes, indent + 1)
                lines.extend(sub_lines)
                
        lines.append(f"{pad}}}")
        return lines

    elif type_name in ("method_declaration", "constructor_declaration"):
        sig_parts = []
        is_public_or_protected = False
        for child in node.children:
            if child.type == "modifiers":
                mod_text = get_text(child, source_bytes)
                if "public" in mod_text or "protected" in mod_text:
                    is_public_or_protected = True
            if child.type in ("block", "constructor_body"):
                break
            sig_parts.append(get_text(child, source_bytes))
        
        parent_type = node.parent.type if node.parent else ""
        if parent_type in ("interface_body", "record_body"):
            is_public_or_protected = True
            
        if is_public_or_protected:
            sig = clean_signature(sig_parts)
            if sig:
                lines.append(f"{pad}{sig};")
        return lines

    elif type_name == "field_declaration":
        is_public_or_protected = False
        text = get_text(node, source_bytes)
        for child in node.children:
            if child.type == "modifiers":
                mod_text = get_text(child, source_bytes)
                if "public" in mod_text or "protected" in mod_text:
                    is_public_or_protected = True
        if is_public_or_protected:
            sig = clean_signature([text])
            lines.append(f"{pad}{sig}")
        return lines

    return lines

def process_repo(root_dir):
    modules = [
        ("graphorm-core", "Domain metadata, view models, AST interfaces, execution abstractions"),
        ("graphorm-schema", "Lexer, Parser, AST, Schema validation (models, views, composite types)"),
        ("graphorm-jdbc", "Dialects, Query builders, joins/aggregations, Connection/Tx wrappers"),
        ("graphorm-graphql", "GraphQL Java engine, DataFetcher generation, Selection-set analysis"),
        ("graphorm-generator", "Code generation engine (Java records, view records, clients, inputs)"),
        ("graphorm-spring-boot-starter", "Spring Boot 3+ AutoConfiguration, DataSource binding"),
        ("graphorm-cli", "Command-line interface for codegen, schema validation, linting"),
    ]
    
    output_lines = [
        "# GraphORM Codebase Map (Tree-Sitter Symbol Index)",
        "",
        "> **Agent Instruction**: Consult this map first before reading source files. It lists all public classes, interfaces, records, and method signatures across the codebase to eliminate exploratory file reads.",
        "",
        "## Modules Overview",
        "",
        "| Module | Responsibility | Source Files |",
        "| :--- | :--- | :--- |"
    ]
    
    mod_sections = []
    total_files = 0
    total_types = 0
    start_time = time.time()
    
    for mod_name, mod_desc in modules:
        mod_path = Path(root_dir) / mod_name / "src" / "main" / "java"
        if not mod_path.exists():
            continue
            
        java_files = sorted(list(mod_path.glob("**/*.java")))
        output_lines.append(f"| [`{mod_name}`](#module-{mod_name.replace('-', '')}) | {mod_desc} | {len(java_files)} |")
        
        mod_section = [f"\n---\n\n## Module `{mod_name}`\n"]
        if not java_files:
            mod_section.append("_No source files found._\n")
            mod_sections.append("\n".join(mod_section))
            continue
            
        for jf in java_files:
            rel_path = jf.relative_to(root_dir)
            try:
                with open(jf, "rb") as f:
                    content = f.read()
                tree = parser.parse(content)
                
                file_types = []
                for child in tree.root_node.children:
                    if child.type in ("class_declaration", "interface_declaration", "record_declaration", "enum_declaration"):
                        file_types.extend(extract_signatures(child, content, indent=1))
                        total_types += 1
                
                if file_types:
                    mod_section.append(f"### `{rel_path}`")
                    mod_section.append("```java")
                    mod_section.extend(file_types)
                    mod_section.append("```\n")
                    total_files += 1
            except Exception as e:
                mod_section.append(f"<!-- Error parsing {rel_path}: {e} -->")
                
        mod_sections.append("\n".join(mod_section))
        
    elapsed = time.time() - start_time
    output_lines.append("")
    output_lines.append(f"**Total Indexed**: {total_files} Java source files, {total_types} types across {len(modules)} modules in **{elapsed:.2f}s**.\n")
    output_lines.extend(mod_sections)
    return "\n".join(output_lines)

if __name__ == "__main__":
    repo_root = Path(__file__).resolve().parent.parent.parent
    result = process_repo(repo_root)
    out_file = repo_root / "REPO_MAP.md"
    with open(out_file, "w") as f:
        f.write(result)
    print(f"Generated {out_file} successfully in {repo_root}!")
