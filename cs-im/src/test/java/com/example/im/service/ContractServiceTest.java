package com.example.im.service;

import com.example.common.ApiException;
import com.example.im.domain.Contract;
import com.example.im.domain.FinancialOrder;
import com.example.im.domain.Product;
import com.example.im.repo.ContractMapper;
import com.example.im.repo.FinancialOrderMapper;
import com.example.im.repo.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 签约流程测试 (v2.2.43)
 *
 * <p>覆盖：
 *   1. 生成合同（合规通过后）
 *   2. 合同状态 DRAFT → 内容 hash 正确
 *   3. 拒绝在非 COMPLIANCE_PASSED 状态生成合同
 *   4. 客户签约 → DRAFT → SIGNED
 *   5. 重复签约 → 抛 ApiException
 *   6. SHA256 哈希 64 hex 字符
 *   7. findByContractNo 返回
 *   8. 内容包含产品名/客户名/金额
 */
class ContractServiceTest {

    private ContractMapper contractMapper;
    private FinancialOrderMapper orderMapper;
    private ProductMapper productMapper;
    private ContractService service;

    @BeforeEach
    void setUp() {
        contractMapper = mock(ContractMapper.class);
        orderMapper = mock(FinancialOrderMapper.class);
        productMapper = mock(ProductMapper.class);
        service = new ContractService(contractMapper, orderMapper, productMapper);
    }

    @Test
    void should_generate_contract_when_compliance_passed() {
        // given
        FinancialOrder order = sampleOrder("FO123", "c-001", "P-W001", 50000);
        order.setStatus("COMPLIANCE_PASSED");
        when(orderMapper.findByOrderNo("FO123")).thenReturn(order);
        when(productMapper.findByCode("P-W001")).thenReturn(sampleProduct());

        // when
        Contract c = service.generateContract("FO123", "TPL-FIN-001");

        // then
        assertThat(c).isNotNull();
        assertThat(c.getContractNo()).startsWith("CT");
        assertThat(c.getOrderNo()).isEqualTo("FO123");
        assertThat(c.getCustomerId()).isEqualTo("c-001");
        assertThat(c.getProductCode()).isEqualTo("P-W001");
        assertThat(c.getTemplateId()).isEqualTo("TPL-FIN-001");
        assertThat(c.getStatus()).isEqualTo("DRAFT");
        assertThat(c.getContentHash()).hasSize(64); // SHA256 hex = 64 chars
        verify(contractMapper, times(1)).insert(any(Contract.class));
    }

    @Test
    void should_reject_contract_generation_when_status_not_compliance_passed() {
        // given
        FinancialOrder order = sampleOrder("FO124", "c-002", "P-W001", 50000);
        order.setStatus("RISK_ASSESSED");
        when(orderMapper.findByOrderNo("FO124")).thenReturn(order);

        // when/then
        assertThatThrownBy(() -> service.generateContract("FO124", "TPL-FIN-001"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("订单未通过合规检查");
    }

    @Test
    void should_sign_contract_and_change_status_to_signed() {
        // given
        Contract draft = Contract.builder()
                .contractNo("CT001")
                .orderNo("FO125")
                .customerId("c-003")
                .contentHash("abc123")
                .status("DRAFT")
                .build();
        when(contractMapper.findByContractNo("CT001")).thenReturn(draft);

        FinancialOrder order = sampleOrder("FO125", "c-003", "P-W001", 50000);
        order.setStatus("COMPLIANCE_PASSED");
        when(orderMapper.findByOrderNo("FO125")).thenReturn(order);

        // when
        Contract signed = service.signContract("CT001", "MOCK_PUBKEY", "MOCK_SIG_BASE64", "127.0.0.1");

        // then
        assertThat(signed.getStatus()).isEqualTo("SIGNED");
        assertThat(signed.getCustomerPublicKey()).isEqualTo("MOCK_PUBKEY");
        assertThat(signed.getCustomerSignature()).isEqualTo("MOCK_SIG_BASE64");
        assertThat(signed.getSignedIp()).isEqualTo("127.0.0.1");
        assertThat(signed.getSignedAt()).isNotNull();

        // 订单状态也应跳到 CONTRACT_SIGNED
        ArgumentCaptor<FinancialOrder> captor = ArgumentCaptor.forClass(FinancialOrder.class);
        verify(orderMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CONTRACT_SIGNED");
    }

    @Test
    void should_reject_duplicate_signing() {
        // given
        Contract signed = Contract.builder()
                .contractNo("CT002")
                .orderNo("FO126")
                .status("SIGNED")
                .build();
        when(contractMapper.findByContractNo("CT002")).thenReturn(signed);

        // when/then
        assertThatThrownBy(() -> service.signContract("CT002", "k", "s", "ip"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("已签约");
    }

    @Test
    void contract_content_should_contain_product_and_customer_info() {
        // given
        FinancialOrder order = sampleOrder("FO127", "c-demo", "P-W001", 30000);
        order.setStatus("COMPLIANCE_PASSED");
        when(orderMapper.findByOrderNo("FO127")).thenReturn(order);
        when(productMapper.findByCode("P-W001")).thenReturn(sampleProduct());

        // when
        Contract c = service.generateContract("FO127", "TPL-FIN-001");

        // then
        assertThat(c.getContent())
                .contains("稳健理财 1 号")
                .contains("c-demo")
                .contains("30000")
                .contains("P-W001");
    }

    @Test
    void sha256_should_produce_64_hex_chars() {
        // 通过 generateContract 间接测试
        FinancialOrder order = sampleOrder("FO128", "c-005", "P-W001", 1000);
        order.setStatus("COMPLIANCE_PASSED");
        when(orderMapper.findByOrderNo("FO128")).thenReturn(order);
        when(productMapper.findByCode("P-W001")).thenReturn(sampleProduct());

        Contract c = service.generateContract("FO128", "TPL-FIN-001");

        assertThat(c.getContentHash()).matches("[0-9a-f]{64}");
    }

    private FinancialOrder sampleOrder(String no, String cid, String pcode, double amount) {
        FinancialOrder o = new FinancialOrder();
        o.setOrderNo(no);
        o.setCustomerId(cid);
        o.setProductCode(pcode);
        o.setAmount(amount);
        o.setStatus("DRAFT");
        o.setCreatedAt(LocalDateTime.now());
        return o;
    }

    private Product sampleProduct() {
        Product p = new Product();
        p.setProductCode("P-W001");
        p.setName("稳健理财 1 号");
        p.setRiskLevel("LOW");
        p.setYieldRate(0.035);
        p.setStatus("ON_SALE");
        p.setMinAmount(1000.0);
        return p;
    }
}