const path = require("path");
const asarNode = require(path.join(__dirname, "asar-node"));
asarNode.register();
module.exports = asarNode;
