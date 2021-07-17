#!/usr/bin/env node
const fs = require("fs");
const { javro, SchemaRegistryAvroFetcher } = require("javro");

var argv = require("yargs")(process.argv.slice(2))
  .scriptName("jsonschema-to-avro")
  .command('$0 <inpath> <outpath> [args]', 'convert a JSON Schema to .avsc Avro schema', (yargs) => {
    yargs
      .positional("inpath", {
        type: "string",
        describe: "path to JSON Schema file to convert"
      })
      .positional("outpath", {
        type: "string",
        describe: "path to save converted Avro schema (.avsc)"
      })
  })
  .option("n", {
      alias: "namespace",
      default: "",
      describe: "see http://avro.apache.org/docs/current/spec.html#names",
      type: "string"
  })
  .help()
  .version(false)
  .argv;

javro({
  jsonSchemaFile: argv.inpath,
  namespace: argv.namespace,
}).then((result) => {
  try {
    fs.writeFileSync(argv.outpath, JSON.stringify(result.avsc, null, 2))
  } catch (err) {
    console.error(err)
  }
});
