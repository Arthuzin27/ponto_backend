-- V3: localizacao do dispositivo no momento da marcacao (informada pelo navegador, via Geolocation API).
-- Colunas opcionais: marcacoes anteriores a esta versao nao tem localizacao. A obrigatoriedade para novas
-- marcacoes e regra da aplicacao (APP_LOCATION_REQUIRED).

ALTER TABLE marcacao
    ADD COLUMN latitude        DOUBLE PRECISION,
    ADD COLUMN longitude       DOUBLE PRECISION,
    -- Raio de incerteza informado pelo dispositivo, em metros.
    ADD COLUMN precisao_metros DOUBLE PRECISION,
    ADD CONSTRAINT ck_marcacao_latitude CHECK (latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_marcacao_longitude CHECK (longitude BETWEEN -180 AND 180),
    ADD CONSTRAINT ck_marcacao_precisao CHECK (precisao_metros >= 0),
    -- Latitude e longitude sempre juntas.
    ADD CONSTRAINT ck_marcacao_localizacao_completa CHECK ((latitude IS NULL) = (longitude IS NULL));
