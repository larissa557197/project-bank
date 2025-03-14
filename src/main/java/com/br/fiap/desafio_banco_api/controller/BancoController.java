package com.br.fiap.desafio_banco_api.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.br.fiap.desafio_banco_api.model.Conta;
import com.br.fiap.desafio_banco_api.model.PixRequest;

@RestController
@RequestMapping("/contas")
public class BancoController {

    // logger para registrar informações no console
    private final Logger log = LoggerFactory.getLogger(getClass());
    // lista que armazena as contas criadas
    private static final List<Conta> contas = new ArrayList<>();
    // contador para gerar o ID das contas
    private static Long contadorId = 1L;

    // Listar todas as contas
    @GetMapping
    public List<Conta> listarContas() {
        return contas;
    }

    // Criar uma conta
    @PostMapping
    // Retorna o status HTTP 201 (Created) ao criar uma conta.
    @ResponseStatus(HttpStatus.CREATED)
    public Conta create(@RequestBody Conta conta) {
        log.info("Cadastrando conta: " + conta);
        // valida os dados da conta antes de criar
        validarConta(conta);
        // seta o ID e a data de abertura da conta
        conta.setId(contadorId++);
        // seta a conta como ativa
        conta.setAtiva(true);
        // seta a data de abertura como a data atual
        conta.setDataAbertura(LocalDate.now());
        // adiciona a conta na lista de contas
        contas.add(conta);
        // retorna a conta criada
        return conta;

    }

    // Buscar por ID
    @GetMapping("{id}")
    public ResponseEntity<Conta> buscarPorId(@PathVariable Long id) {
        log.info("Buscando conta ID: " + id);
        // Retorna a conta encontrada ou lança um erro caso não exista.
        return ResponseEntity.ok(getConta(id));
    }

    // Buscar conta por CPF
    @GetMapping("cpf/{cpf}")
    public ResponseEntity<Conta> buscarPorCpf(@PathVariable String cpf) {
        log.info("Buscando conta pelo CPF: " + cpf);
        return contas.stream()
                .filter(c -> c.getCpfTitular().equals(cpf))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada"));

    }

    // Encerrar conta -> marcar como INATIVA
    @PutMapping("{id}/encerrar")
    public ResponseEntity<Conta> encerrarConta(@PathVariable Long id) {
        log.info("Encerranod conta ID: " + id);
        Conta conta = getConta(id);
        // Define a conta como inativa.
        conta.setAtiva(false);
        return ResponseEntity.ok(conta);
    }

    // DEPÓSITO
    // realizando o depósito
    @PutMapping("{id}/deposito")
    public ResponseEntity<Conta> depositar(@PathVariable Long id, @RequestBody Double valor) {
        log.info("Depósito na conta ID: " + id + "| Valor: " + valor);

        if (valor <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor do depósito deve ser positivo");
        }
        for (Conta conta : contas) {
            if (conta.getId() == id) {
                // Atualiza o saldo.
                conta.setSaldoInicial(conta.getSaldoInicial() + valor);
                log.info("Depósito de R$ {} realizado na conta {}", valor, id);
                return ResponseEntity.ok(conta);
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada");
    }

    // SAQUE
    // realizando o saque
    @PutMapping("{id}/saque")
    public ResponseEntity<Conta> sacar(@PathVariable Long id, @RequestBody Double valor) {
        log.info("Saque na conta ID: " + id + "| Valor: " + valor);

        if (valor <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor do depósito deve ser positivo");
        }
        for (Conta conta : contas) {
            if (conta.getId() == id) {
                if (conta.getSaldoInicial() < valor) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente para realizar o saque");
                }
                // Atualiza o saldo.
                conta.setSaldoInicial(conta.getSaldoInicial() - valor);
                log.info("Saque de R$ {} realizado na conta {}", valor, id);
                return ResponseEntity.ok(conta);
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada");
    }

    // PIX
    // realizando um pix
    @PutMapping("{idOrigem}/pix")
    public ResponseEntity<Conta> realizarPix(@PathVariable Long idOrigem, @RequestBody PixRequest pixRequest) {
        log.info("PIX de ID: " + idOrigem + "para ID: " + pixRequest.getIdDestino() + "| Valor: " + pixRequest.getValor());

       // validações
       if (pixRequest.getValor() == null || pixRequest.getValor() <= 0) {
           throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor do PIX deve ser positivo");
        
       }

        Conta contaOrigem = getConta(idOrigem);
        Conta contaDestino = getConta(pixRequest.getIdDestino());

        if (!contaOrigem.isAtiva() || !contaDestino.isAtiva()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ambas as contas devem estar ativas para realizar o PIX");
        }

        if (contaOrigem.getSaldoInicial() < pixRequest.getValor()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente para realizar o PIX");  
        }

        // realiza a tranferência
        contaOrigem.setSaldoInicial(contaOrigem.getSaldoInicial() - pixRequest.getValor());
        contaDestino.setSaldoInicial(contaDestino.getSaldoInicial() + pixRequest.getValor());
    
        return ResponseEntity.ok(contaOrigem);
    }

    // métodos auxiliares
    private Conta getConta(Long id) {
        return contas.stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada"));
    }

    // método para validar a conta
    private void validarConta(Conta conta) {
        if (conta.getNomeTitular() == null || conta.getNomeTitular().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do titular é obrigatório");
        }
        if (conta.getCpfTitular() == null || conta.getCpfTitular().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF do titular é obrigatório");
        }
        if (conta.getDataAbertura() != null && conta.getDataAbertura().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data de abertura não pode ser no futuro");
        }
        if (conta.getSaldoInicial() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo inicial não pode ser negativo");
        }
        if (conta.getTipo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O tipo da conta deve ser válido");
        }
    }

}
