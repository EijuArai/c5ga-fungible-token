package com.r3.token.fungible.workflows

import com.r3.corda.ledger.utxo.fungible.NumericDecimal
import com.r3.corda.ledger.utxo.ownable.query.OwnableStateQueries
import com.r3.sum
import com.r3.token.fungible.states.Token
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.math.BigDecimal
import java.time.Instant

@InitiatingFlow(protocol = "get-token-balance")
@Suppress("unused")
class GetTokenBalanceFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    data class MoveTokenRequest(
        val issuer: String,
        val owner: String
    )

    @CordaInject
    private lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    private lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    private lateinit var memberLookup: MemberLookup

    @CordaInject
    private lateinit var flowMessaging: FlowMessaging

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @CordaInject
    private lateinit var digestService: DigestService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("GetTokenBalanceFlow.call() called")

        val request = requestBody.getRequestBodyAs(jsonMarshallingService, MoveTokenRequest::class.java)

        val issuerX500Name = MemberX500Name.parse(request.issuer)
        val ownerX500Name = MemberX500Name.parse(request.owner)
        val myInfo = memberLookup.myInfo()

        val issuer = requireNotNull(memberLookup.lookup(issuerX500Name)) {
            "Failed to obtain member information for the specified name: ${request.issuer}."
        }
        val owner = requireNotNull(memberLookup.lookup(ownerX500Name)) {
            "Failed to obtain member information for the specified name: ${request.owner}."
        }
        if (myInfo.name != ownerX500Name) {
            throw IllegalArgumentException("Owner should be Initiator.")
        }

        val issuerKeys = issuer.ledgerKeys
        val ownerKeys = owner.ledgerKeys

        val availableTokens = try {
            utxoLedgerService.query(OwnableStateQueries.GET_BY_OWNER, StateAndRef::class.java)
                .setCreatedTimestampLimit(Instant.now())
                .setLimit(50)
                .setOffset(0)
                .setParameter(
                    "owner",
                    digestService.hash(ownerKeys.first().encoded, DigestAlgorithmName.SHA2_256).toString()
                )
                .setParameter("stateType", Token::class.java.name)
                .execute()
                .results
                .filterIsInstance<StateAndRef<Token>>()
                .filter { it.state.contractState.issuer in issuerKeys }
                .filter { it.state.contractState.owner in ownerKeys }
                .map { it.state.contractState.quantity }
                .sum()
        } catch (e: NoSuchElementException) {
            NumericDecimal(BigDecimal.ZERO)
        }

        log.info("Querying Token has been finished")

        return ("Token balance of " + request.owner + " is " + availableTokens.toString())
    }
}
/*
{
    "clientRequestId": "get-1",
    "flowClassName": "com.r3.token.fungible.workflows.GetTokenBalanceFlow",
    "requestBody": {
    "issuer": "CN=Alice, OU=Test Dept, O=R3, L=London, C=GB",
    "owner": "CN=Bob, OU=Test Dept, O=R3, L=London, C=GB"
    }
}
*/