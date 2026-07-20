ALTER TABLE planos_semanais
    DROP CONSTRAINT uk_planos_semanais_usuario_data;

CREATE UNIQUE INDEX uk_planos_semanais_usuario_data_nao_cancelado
    ON planos_semanais (usuario_id, data_inicial)
    WHERE estado <> 'CANCELADO';

COMMENT ON INDEX uk_planos_semanais_usuario_data_nao_cancelado IS
    'Mantem um unico plano corrente por semana e preserva planos cancelados no historico.';
