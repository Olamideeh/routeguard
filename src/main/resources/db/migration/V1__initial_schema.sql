--
-- PostgreSQL database dump
--


-- Dumped from database version 16.14
-- Dumped by pg_dump version 16.14




--
-- Name: company_api_credentials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.company_api_credentials (
    id uuid NOT NULL,
    active boolean NOT NULL,
    api_key_hash character varying(64) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone,
    key_id uuid NOT NULL,
    last_used_at timestamp(6) with time zone,
    version bigint NOT NULL,
    company_id uuid NOT NULL
);


--
-- Name: delivery_companies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.delivery_companies (
    id uuid NOT NULL,
    company_code character varying(50) NOT NULL,
    country_code character varying(2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    name character varying(150) NOT NULL,
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT delivery_companies_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying])::text[])))
);


--
-- Name: delivery_evaluations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.delivery_evaluations (
    id uuid NOT NULL,
    decision character varying(30) NOT NULL,
    decision_explanation text NOT NULL,
    evaluated_at timestamp(6) with time zone NOT NULL,
    gps_distance_metres numeric(12,2),
    photo_reused boolean,
    recovery_action character varying(40) NOT NULL,
    risk_score integer NOT NULL,
    delivery_event_id uuid NOT NULL,
    CONSTRAINT delivery_evaluations_decision_check CHECK (((decision)::text = ANY ((ARRAY['VERIFIED'::character varying, 'SUSPICIOUS'::character varying, 'REVIEW_REQUIRED'::character varying])::text[]))),
    CONSTRAINT delivery_evaluations_recovery_action_check CHECK (((recovery_action)::text = ANY ((ARRAY['NO_ACTION'::character varying, 'RETRY_DELIVERY'::character varying, 'CORRECT_ADDRESS'::character varying, 'USE_PICKUP_POINT'::character varying, 'CONTACT_CUSTOMER'::character varying, 'REASSIGN_RIDER'::character varying, 'RETURN_TO_SENDER'::character varying, 'MANUAL_INVESTIGATION'::character varying])::text[])))
);


--
-- Name: delivery_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.delivery_events (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    customer_contact_attempted boolean,
    customer_id character varying(150) NOT NULL,
    delivery_latitude numeric(10,7),
    delivery_longitude numeric(10,7),
    event_timestamp timestamp(6) with time zone NOT NULL,
    event_type character varying(30) NOT NULL,
    expected_latitude numeric(10,7),
    expected_longitude numeric(10,7),
    external_delivery_id character varying(150) NOT NULL,
    failure_reason character varying(500),
    idempotency_key character varying(100) NOT NULL,
    otp_verified boolean,
    proof_photo_hash character varying(64),
    reference character varying(80) NOT NULL,
    request_payload_hash character varying(64),
    rider_id character varying(150) NOT NULL,
    status character varying(30) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    company_id uuid NOT NULL,
    CONSTRAINT delivery_events_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['DELIVERY_ATTEMPTED'::character varying, 'DELIVERED'::character varying, 'DELIVERY_FAILED'::character varying])::text[]))),
    CONSTRAINT delivery_events_status_check CHECK (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'EVALUATING'::character varying, 'VERIFIED'::character varying, 'SUSPICIOUS'::character varying, 'REVIEW_REQUIRED'::character varying, 'UNDER_REVIEW'::character varying, 'RESOLVED'::character varying, 'PROCESSING_FAILED'::character varying])::text[])))
);


--
-- Name: evaluation_reason_codes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evaluation_reason_codes (
    evaluation_id uuid NOT NULL,
    reason_code character varying(50) NOT NULL,
    CONSTRAINT evaluation_reason_codes_reason_code_check CHECK (((reason_code)::text = ANY ((ARRAY['ALL_EVIDENCE_VERIFIED'::character varying, 'GPS_EVIDENCE_MISSING'::character varying, 'GPS_DISTANCE_EXCEEDED'::character varying, 'OTP_EVIDENCE_MISSING'::character varying, 'OTP_INVALID'::character varying, 'PHOTO_EVIDENCE_MISSING'::character varying, 'PHOTO_REUSED'::character varying, 'CONTACT_ATTEMPT_MISSING'::character varying, 'FAILURE_REASON_MISSING'::character varying, 'EVENT_TIMESTAMP_STALE'::character varying, 'EVIDENCE_CONFLICT'::character varying, 'INSUFFICIENT_EVIDENCE'::character varying])::text[])))
);


--
-- Name: platform_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_users (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(200) NOT NULL,
    full_name character varying(150) NOT NULL,
    password_hash character varying(255) NOT NULL,
    role character varying(30) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    company_id uuid,
    CONSTRAINT platform_users_role_check CHECK (((role)::text = ANY ((ARRAY['PLATFORM_ADMIN'::character varying, 'COMPANY_ADMIN'::character varying, 'OPERATIONS_OFFICER'::character varying, 'RISK_REVIEWER'::character varying])::text[])))
);


--
-- Name: risk_reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.risk_reviews (
    id uuid NOT NULL,
    decision character varying(30) NOT NULL,
    notes character varying(2000) NOT NULL,
    recovery_action character varying(40) NOT NULL,
    reviewed_at timestamp(6) with time zone NOT NULL,
    delivery_event_id uuid NOT NULL,
    reviewer_id uuid NOT NULL,
    CONSTRAINT risk_reviews_decision_check CHECK (((decision)::text = ANY ((ARRAY['CONFIRMED_VALID'::character varying, 'CONFIRMED_SUSPICIOUS'::character varying, 'INCONCLUSIVE'::character varying])::text[]))),
    CONSTRAINT risk_reviews_recovery_action_check CHECK (((recovery_action)::text = ANY ((ARRAY['NO_ACTION'::character varying, 'RETRY_DELIVERY'::character varying, 'CORRECT_ADDRESS'::character varying, 'USE_PICKUP_POINT'::character varying, 'CONTACT_CUSTOMER'::character varying, 'REASSIGN_RIDER'::character varying, 'RETURN_TO_SENDER'::character varying, 'MANUAL_INVESTIGATION'::character varying])::text[])))
);


--
-- Name: company_api_credentials company_api_credentials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_api_credentials
    ADD CONSTRAINT company_api_credentials_pkey PRIMARY KEY (id);


--
-- Name: delivery_companies delivery_companies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_companies
    ADD CONSTRAINT delivery_companies_pkey PRIMARY KEY (id);


--
-- Name: delivery_evaluations delivery_evaluations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_evaluations
    ADD CONSTRAINT delivery_evaluations_pkey PRIMARY KEY (id);


--
-- Name: delivery_events delivery_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_events
    ADD CONSTRAINT delivery_events_pkey PRIMARY KEY (id);


--
-- Name: evaluation_reason_codes evaluation_reason_codes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evaluation_reason_codes
    ADD CONSTRAINT evaluation_reason_codes_pkey PRIMARY KEY (evaluation_id, reason_code);


--
-- Name: platform_users platform_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_users
    ADD CONSTRAINT platform_users_pkey PRIMARY KEY (id);


--
-- Name: risk_reviews risk_reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.risk_reviews
    ADD CONSTRAINT risk_reviews_pkey PRIMARY KEY (id);


--
-- Name: company_api_credentials uk_api_credential_key_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_api_credentials
    ADD CONSTRAINT uk_api_credential_key_id UNIQUE (key_id);


--
-- Name: delivery_events uk_company_idempotency_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_events
    ADD CONSTRAINT uk_company_idempotency_key UNIQUE (company_id, idempotency_key);


--
-- Name: delivery_companies uk_delivery_company_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_companies
    ADD CONSTRAINT uk_delivery_company_code UNIQUE (company_code);


--
-- Name: delivery_events uk_delivery_event_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_events
    ADD CONSTRAINT uk_delivery_event_reference UNIQUE (reference);


--
-- Name: delivery_evaluations uk_evaluation_delivery_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_evaluations
    ADD CONSTRAINT uk_evaluation_delivery_event UNIQUE (delivery_event_id);


--
-- Name: platform_users uk_platform_user_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_users
    ADD CONSTRAINT uk_platform_user_email UNIQUE (email);


--
-- Name: idx_api_credential_company; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_api_credential_company ON public.company_api_credentials USING btree (company_id);


--
-- Name: idx_event_external_delivery; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_event_external_delivery ON public.delivery_events USING btree (company_id, external_delivery_id);


--
-- Name: idx_event_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_event_status ON public.delivery_events USING btree (status);


--
-- Name: idx_risk_review_event; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_risk_review_event ON public.risk_reviews USING btree (delivery_event_id, reviewed_at);


--
-- Name: idx_risk_review_reviewer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_risk_review_reviewer ON public.risk_reviews USING btree (reviewer_id);


--
-- Name: company_api_credentials fk_api_credential_company; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_api_credentials
    ADD CONSTRAINT fk_api_credential_company FOREIGN KEY (company_id) REFERENCES public.delivery_companies(id);


--
-- Name: delivery_events fk_delivery_event_company; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_events
    ADD CONSTRAINT fk_delivery_event_company FOREIGN KEY (company_id) REFERENCES public.delivery_companies(id);


--
-- Name: delivery_evaluations fk_evaluation_delivery_event; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_evaluations
    ADD CONSTRAINT fk_evaluation_delivery_event FOREIGN KEY (delivery_event_id) REFERENCES public.delivery_events(id);


--
-- Name: platform_users fk_platform_user_company; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_users
    ADD CONSTRAINT fk_platform_user_company FOREIGN KEY (company_id) REFERENCES public.delivery_companies(id);


--
-- Name: risk_reviews fk_risk_review_event; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.risk_reviews
    ADD CONSTRAINT fk_risk_review_event FOREIGN KEY (delivery_event_id) REFERENCES public.delivery_events(id);


--
-- Name: risk_reviews fk_risk_review_reviewer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.risk_reviews
    ADD CONSTRAINT fk_risk_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES public.platform_users(id);


--
-- Name: evaluation_reason_codes fkfr4vr80mkq4gsj847922tmpyn; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evaluation_reason_codes
    ADD CONSTRAINT fkfr4vr80mkq4gsj847922tmpyn FOREIGN KEY (evaluation_id) REFERENCES public.delivery_evaluations(id);


--
-- PostgreSQL database dump complete
--


