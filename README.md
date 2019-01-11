# Export Nodes

The `export-nodes` utility is a program that reads content from an Oak Segment Store and File Data Store and dumps it a portable, loss-less text format.

## Usage

    export-nodes [--file-data-store=PATH] --segment-store=PATH
   
The `--segment-store` option specifies the path to the Segment Store and is mandatory. If you have a File Data Store, you need to specify its path with the `--file-data-store` option.

**Warning** You need to be careful when specifying the path to the File Data Store. If the File Data Store is located at `/path/to/data/repository/datastore`, you need to pass `/path/to/data` to the `--file-data-store` option. Always strip the `repository/datastore` suffix from the File Data Store path.

The command prints the dump of the content on the standard output. The output is generally large, so you probably want to redirect it to a file.

    export-nodes [...] >export.txt
    
You can also compress the output on the fly using standard Unix tools.

    export-nodes [...] | gzip >export.txt.gz
    
## Format

The format of the export represent a traversal through the repository content. The format is easier to understand if you keep the traversal metaphor in mind. The output is meant to be consumed in streaming mode.

The export line-based, with one command per line. A command is represented by a letter and may have one or more arguments separated by spaces. Sometimes the last argument of a command extends until the end of the line. A command is always terminated by a new-line (`\n`) character.

The format includes the following commands.

    r
    
The `r` command represents the beginning of the export. While processing the output, every line preceding the `r` command should be discarded. The `r` command is emitted while traversing the root node.

    c NAME
    
The `c` command represents the traversal of a node `NAME` nested under the current node. While processing the output, this command begins a new context for the processing of following commands.

    p TYPE NAME
     
The `p` command represents the traversal of a property `NAME` of type `TYPE`. The property is attached to the node in the current context. While processing the output, this command begins a new context of the processing of following commands. `NAME` might contain spaces and extends until the end of the line.

    v VALUE
    
The `v` command represent the traversal of a property value with content `VALUE`. The value is attached to the property in the current context. `VALUE` might contain spaces and extends until the end of the line. `VALUE` might contain escape sequences for `\` and new-line, represented as `\\` and `\n` respectively. While processing the output, `VALUE` should be unescaped.

    x DATA
    
The `x` command represents the traversal of a property value containing binary data `DATA`, encoded as Base64. The value is attached to the property in the current context.

    ^
    
The `^` command represents the end of the current context, and a movement "up" the content tree. This command terminates a context for the root, a child, or a property. When processing the output, if the `^` command terminates the context of the root, the export can be considered complete. Further lines in the output should be discarded.

## Examples

This section contains examples of content and the corresponding exports.

### Example 1

Your content consists of only the root node. The root has two properties of type `STRING`, `foo` and `bar`. The values of these properties are `oof` and `rab`.

    r
    p STRING foo
    v oof
    ^
    p STRING bar
    v rab
    ^
    ^
    
### Example 2

Your content consists of only the root node. The root has one multi-value property `foo` of type `STRINGS`. The values for these property are `bar` and `baz`.

    r
    p STRINGS
    v bar
    v baz
    ^
    ^

### Example 3

Your content consists of the root node and its two children, `foo` and `bar`. The two children have no properties.

    r
    c foo
    ^
    c bar
    ^
    ^
    
### Example 4

Your content consists of a root node and its child `foo`. The node `foo` has two properties of type `STRING`, `bar` and `baz`, with values `rab` and `zab` respectively.

    r
    c foo
    p STRING bar
    v rab
    ^
    p STRING baz
    v zab
    ^
    ^
    ^
    
## License

This software is released under the MIT license.