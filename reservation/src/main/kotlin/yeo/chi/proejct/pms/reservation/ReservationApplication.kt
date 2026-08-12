package yeo.chi.proejct.pms.reservation

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

// reservation depends on operation (see reservation/build.gradle.kts), so its own Spring context must
// also pick up operation's beans/entities. Operation's own basePackage is deliberately left out of
// componentScan and only its leaf packages are listed, so OperationApplication (operation's own
// @SpringBootApplication, which brings its own @EnableAutoConfiguration) is never swept in by this scan.
@SpringBootApplication
@ComponentScan(
	basePackages = [
		"yeo.chi.proejct.pms.reservation",
		"yeo.chi.proejct.pms.operation.controller",
		"yeo.chi.proejct.pms.operation.service",
		"yeo.chi.proejct.pms.operation.persistent",
		"yeo.chi.proejct.pms.operation.configuration",
	],
)
@EntityScan(
	basePackages = [
		"yeo.chi.proejct.pms.reservation.persistent",
		"yeo.chi.proejct.pms.operation.domain",
	],
)
class ReservationApplication

fun main(args: Array<String>) {
	runApplication<ReservationApplication>(*args)
}
