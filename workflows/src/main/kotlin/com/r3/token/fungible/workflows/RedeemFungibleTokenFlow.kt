package com.r3.token.fungible.workflows

import com.r3.corda.ledger.utxo.fungible.NumericDecimal
import com.r3.corda.ledger.utxo.ownable.query.OwnableStateQueries
import com.r3.sum
import com.r3.token.fungible.contracts.TokenContract
import com.r3.token.fungible.states.Token
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.math.BigDecimal
import java.security.PublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit

@InitiatingFlow(protocol = "redeem-fungible-token")
@Suppress("unused")
class RedeemFungibleTokenFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    data class RedeemFungibleTokenRequest(
        val issuer: String,
        val owner: String,
        val quantity: BigDecimal,
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
        log.info("RedeemFungibleTokenFlow.call() called")

        val request = requestBody.getRequestBodyAs(jsonMarshallingService, RedeemFungibleTokenRequest::class.java)

        val issuerX500Name = MemberX500Name.parse(request.issuer)
        val ownerX500Name = MemberX500Name.parse(request.owner)
        val myInfo = memberLookup.myInfo()

        val issuer = requireNotNull(memberLookup.lookup(issuerX500Name)) {
            "Failed to obtain member information for the specified name: ${request.issuer}."
        }
        if (myInfo.name != ownerX500Name) {
            throw IllegalArgumentException("Owner should be Initiator.")
        }

        val issuerKeys = issuer.ledgerKeys
        val myKeys = myInfo.ledgerKeys

        log.info("Start Token Selection")

        val inputTokens = mutableListOf<StateAndRef<Token>>()
        val targetQuantity = NumericDecimal(request.quantity, 2)
        val availableTokens = utxoLedgerService.query(OwnableStateQueries.GET_BY_OWNER, StateAndRef::class.java)
            .setCreatedTimestampLimit(Instant.now())
            .setLimit(50)
            .setOffset(0)
            .setParameter(
                "owner",
                digestService.hash(myKeys.first().encoded, DigestAlgorithmName.SHA2_256).toString()
            )
            .setParameter("stateType", Token::class.java.name)
            .execute()
            .results
            .filterIsInstance<StateAndRef<Token>>()
            .filter { it.state.contractState.issuer in issuerKeys }
            .filter { it.state.contractState.owner in myKeys }
            .sortedBy { it.state.contractState.quantity }
            .apply { checkSufficientTokenBalance(this, targetQuantity) }

        var remainder = targetQuantity

        for (availableToken in availableTokens) {
            if (remainder <= NumericDecimal.ZERO.setScale(2)) break
            inputTokens.add(availableToken)
            remainder -= availableToken.state.contractState.quantity
        }

        val changeQuantity = inputTokens.map { it.state.contractState.quantity }.sum() - targetQuantity
        val outputToken = if (changeQuantity > NumericDecimal.ZERO.setScale(2)) Token(
            issuerX500Name,
            issuerKeys.first(),
            myKeys.first(),
            changeQuantity
        ) else null

        var issuerKey: PublicKey? = null
        val sessions = mutableListOf<FlowSession>()
        if (issuerX500Name != myInfo.name) {
            issuerKey = issuerKeys.first()
            sessions.add(flowMessaging.initiateFlow(issuerX500Name))
        }

        val transaction = utxoLedgerService.createTransactionBuilder()
            .addInputStates(inputTokens.map { it.ref })
            .apply { outputToken?.let { addOutputStates(it) } }
            .addCommand(TokenContract.Redeem())
            .addSignatories(inputTokens.map { it.state.contractState.owner }.distinct().single())
            .apply { issuerKey?.let { addSignatories(it) } }
            .setNotary(inputTokens.map { it.state.notaryName }.distinct().single())
            .setTimeWindowUntil(Instant.now().plus(1, ChronoUnit.DAYS))
            .toSignedTransaction()

        return try {
            utxoLedgerService.finalize(transaction, sessions)
            log.info("Finalization has been finished")
            "Successfully Redeemed Fungible Token(amount:${request.quantity})"
        } catch (e: Exception) {
            "Flow failed, message: ${e.message}"
        }
    }

    private fun checkSufficientTokenBalance(
        availableTokens: Iterable<StateAndRef<Token>>,
        targetQuantity: NumericDecimal
    ) {
        val availableQuantity = availableTokens.map { it.state.contractState.quantity }.sum()
        check(availableQuantity >= targetQuantity) {
            "Insufficient token balance available to perform redeem operation: Target = $targetQuantity, Available = $availableQuantity."
        }
    }
}

@InitiatedBy(protocol = "redeem-fungible-token")
class RedeemFungibleTokenResponderFlow : ResponderFlow {

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val finalizedSignedTransaction = utxoLedgerService.receiveFinality(session) { ledgerTransaction ->
                val states = ledgerTransaction.outputContractStates
                // Check something for SampleToken if you need
            }
            log.info("Finished responder flow - $finalizedSignedTransaction")
        } catch (e: Exception) {
            log.warn("Exceptionally finished responder flow", e)
        }
    }
}


/*
{
  "clientRequestId": "redeem-1",
  "flowClassName": "com.r3.token.fungible.workflows.RedeemFungibleTokenFlow",
  "requestBody": {
    "issuer": "CN=Alice, OU=Test Dept, O=R3, L=London, C=GB",
    "owner": "CN=Bob, OU=Test Dept, O=R3, L=London, C=GB",
    "quantity": 50
  }
}
*/
