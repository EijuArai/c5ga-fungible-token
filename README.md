# サンプルFungibleTokenアプリ（FungibleState準拠Ver）

Corda5 GA版で提供されているFungibleState, WellKnownIssuableState, OwnableStateを実装したFungibleTokenを作成しました．
FlowについてはTokenの発行Flow，Tokenの移転Flow，Tokenの残高照会Flowを実装してみました．（Tokenの償還Flowは余裕があったらやります．．．）

### サンプルアプリを触ってみよう

Corda 5ではflowは`POST /flow/{holdingidentityshorthash}`
からトリガーされます．そしてflowのステータスは`GET /flow/{holdingidentityshorthash}/{clientrequestid}`で確認できます．

* holdingidentityshorthash: ネットワーク参加者のIDです．ネットワークの全ての参加者のIDは`ListVNodes`タスクで確認できます．
* clientrequestid: 実行するflowのプロセスに与えられるIDです．

#### Step 1: Tokenを発行しよう

BobにUSDを100単位発行します．
`POST /flow/{holdingidentityshorthash}`に行って，以下のRequest BodyでPOSTしてください．

```
{
  "clientRequestId": "issue-1",
  "flowClassName": "com.r3.token.fungible.workflows.IssueFungibleTokenFlow",
  "requestBody": {
    "issuer": "CN=Alice, OU=Test Dept, O=R3, L=London, C=GB",
    "owner": "CN=Bob, OU=Test Dept, O=R3, L=London, C=GB",
    "quantity": 100
  }
}
```

flowのステータスは`GET /flow/{holdingidentityshorthash}/{clientrequestid}`で確認してください．

#### Step 2: Tokenを移転しよう

Step1でBobに発行したUSDを50単位Aliceに移転します．
`POST /flow/{holdingidentityshorthash}`に行って，以下のRequest BodyでPOSTしてください．
ownerId，issuerIdとownerIdは(ry

```
{
  "clientRequestId": "transfer-1",
  "flowClassName": "com.r3.token.fungible.workflows.TransferFungibleTokenFlow",
  "requestBody": {
    "issuer": "CN=Alice, OU=Test Dept, O=R3, L=London, C=GB",
    "owner": "CN=Bob, OU=Test Dept, O=R3, L=London, C=GB",
    "newOwner": "CN=Alice, OU=Test Dept, O=R3, L=London, C=GB",
    "quantity": 50
  }
}
```

例によってflowのステータスは`GET /flow/{holdingidentityshorthash}/{clientrequestid}`で確認してください．

#### Step 3: Tokenを償還しよう

Step1でBobに発行したUSDを50単位償還します．
`POST /flow/{holdingidentityshorthash}`に行って，以下のRequest BodyでPOSTしてください．
ownerId，issuerIdとownerIdは(ry

```
{
  "clientRequestId": "redeem-1",
  "flowClassName": "com.r3.token.fungible.workflows.RedeemFungibleTokenFlow",
  "requestBody": {
    "issuer": "CN=Alice, OU=Test Dept, O=R3, L=London, C=GB",
    "owner": "CN=Bob, OU=Test Dept, O=R3, L=London, C=GB",
    "quantity": 50
  }
}
```

例によってflowのステータスは`GET /flow/{holdingidentityshorthash}/{clientrequestid}`で確認してください．

#### Step 4: 残高を照会しよう

BobのTokenの残高を確認します．
`POST /flow/{holdingidentityshorthash}`に行って，以下のRequest BodyでPOSTしてください．
ownerId，issuerIdとownerIdは(ry

```
{
    "clientRequestId": "get-1",
    "flowClassName": "com.r3.token.fungible.workflows.GetTokenBalanceFlow",
    "requestBody": {
    "issuer": "CN=Alice, OU=Test Dept, O=R3, L=London, C=GB",
    "owner": "CN=Bob, OU=Test Dept, O=R3, L=London, C=GB"
    }
}
```

例によってflowのステータスは`GET /flow/{holdingidentityshorthash}/{clientrequestid}`で確認してください．