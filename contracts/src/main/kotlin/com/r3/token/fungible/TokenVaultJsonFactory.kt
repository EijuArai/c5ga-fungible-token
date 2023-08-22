package com.r3.token.fungible

import com.r3.token.fungible.states.Token
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory

class TokenVaultJsonFactory : ContractStateVaultJsonFactory<Token> {

    override fun getStateType(): Class<Token> {
        return Token::class.java
    }

    override fun create(state: Token, jsonMarshallingService: JsonMarshallingService): String {
        return "{}"
    }
}